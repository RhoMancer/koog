import re
from urllib import request, error
from urllib.parse import urljoin

try:
    from bs4 import BeautifulSoup  # type: ignore
except Exception:  # pragma: no cover - environment may not have bs4
    BeautifulSoup = None  # fallback handled at runtime

# Simple in-memory cache for URL existence checks during a single MkDocs build run
_URL_EXISTS_CACHE: dict[str, bool] = {}

# Cache for the navigation map built from https://api.koog.ai/navigation.html
_NAV_MAP: dict[str, str] | None = None  # pageid_prefix -> href
_NAV_FETCH_FAILED: bool = False


def _build_navigation_map(timeout: float = 10.0) -> dict[str, str] | None:
    """Fetch and parse navigation.html to build a mapping from pageid prefix to href.

    Returns a dict mapping the part of the pageid before '///' to the <a href> found
    inside the child div with class 'toc--row'. Returns None on failure.
    """
    global _NAV_MAP, _NAV_FETCH_FAILED

    if _NAV_MAP is not None:
        return _NAV_MAP
    if _NAV_FETCH_FAILED:
        return None

    if BeautifulSoup is None:
        # bs4 is not available; can't build the map.
        _NAV_FETCH_FAILED = True
        print("Warning: BeautifulSoup (bs4) not available; API link processing skipped.")
        return None

    NAV_URL = "https://api.koog.ai/navigation.html"
    headers = {"User-Agent": "mkdocs-api-link-processor/1.0"}
    try:
        req = request.Request(NAV_URL, headers=headers, method="GET")
        with request.urlopen(req, timeout=timeout) as resp:
            if not (200 <= resp.status < 300):
                _NAV_FETCH_FAILED = True
                print(f"Warning: Failed to fetch navigation from {NAV_URL}: HTTP {resp.status}")
                return None
            content_type = resp.headers.get("Content-Type", "")
            encoding = "utf-8"
            if "charset=" in content_type:
                try:
                    encoding = content_type.split("charset=", 1)[1].split(";")[0].strip()
                except Exception:
                    pass
            html = resp.read().decode(encoding, errors="replace")
    except Exception as e:
        _NAV_FETCH_FAILED = True
        print(f"Warning: Exception fetching navigation: {e}")
        return None

    try:
        soup = BeautifulSoup(html, "html.parser")
        result: dict[str, str] = {}
        for div in soup.find_all("div", attrs={"pageid": True}):
            pageid: str = div.get("pageid", "")
            if not pageid:
                continue
            # Base prefix: everything before '///' (if present)
            key = pageid.split("///", 1)[0]
            toc_row = div.find("div", class_="toc--row")
            if not toc_row:
                continue
            a = toc_row.find("a")
            if not a:
                continue
            href = a.get("href")
            if not href:
                continue
            # Normalize href: resolve any '../' or relative references against NAV_URL
            try:
                normalized_href = urljoin(NAV_URL, href)
            except Exception:
                normalized_href = href

            # Store multiple key variants to support different source formats
            # 1) Store the original prefix (back-compat)
            if key not in result:
                result[key] = normalized_href

            # 2) If the pageid included '///', the kept part may contain '/' separators
            #    Create and store a dotted variant by replacing all '/' with '.'
            if "///" in pageid:
                dotted_key = key.replace("/", ".")
                if dotted_key not in result:
                    result[dotted_key] = normalized_href

            # 3) Handle function/interface style pageids that include '//'
            #    Keep everything before the first '//' and add the first token
            #    after it (up to the next '/'), discard the rest.
            if "//" in key:
                idx = key.find("//")
                before = key[:idx]
                after = key[idx + 2 :]
                # First token after the double slash up to next '/'
                next_sep = after.find("/")
                token_after = after if next_sep == -1 else after[:next_sep]
                trimmed = f"{before}//{token_after}" if token_after else before.rstrip("/")

                if trimmed and trimmed not in result:
                    result[trimmed] = normalized_href

                # Dotted variant: replace '//' and '/' with '.'
                if trimmed:
                    dotted_trimmed = trimmed.replace("//", ".").replace("/", ".")
                    if dotted_trimmed not in result:
                        result[dotted_trimmed] = normalized_href

        _NAV_MAP = result
        try:
            print(f"Info: API navigation loaded with {len(_NAV_MAP)} entries.")
        except Exception:
            # Be conservative: never fail the build due to logging
            pass
        return _NAV_MAP
    except Exception as e:
        _NAV_FETCH_FAILED = True
        print(f"Warning: Exception parsing navigation HTML: {e}")
        return None


def on_page_markdown(markdown, page, config, files):
    """
    MkDocs hook: transform custom API links in markdown.

    Input pattern:
      [Link text](api:project:module:package:class)

    Output pattern:
      [Link-text](https://api.koog.ai/project/module/package/-class/index.html)
    """

    BASE_URL = "https://api.koog.ai/"

    # Regex to capture markdown links where the URL starts with "api:"
    pattern = re.compile(r"\[([^\]]+)\]\((api:[^\)]+)\)")

    def _extract_project_prefix(k: str) -> str:
        return k.split("::", 1)[0] if "::" in k else ""

    def _suggest_keys(nav_map: dict[str, str], k: str, limit: int = 5) -> list[str]:
        try:
            import difflib  # stdlib
        except Exception:
            return []

        prefix = _extract_project_prefix(k)
        candidates = [key for key in nav_map.keys() if prefix and key.startswith(prefix + "::")]
        if not candidates:
            candidates = list(nav_map.keys())
        # get_close_matches can raise in rare cases if inputs are odd; guard it
        try:
            return difflib.get_close_matches(k, candidates, n=limit, cutoff=0.6)
        except Exception:
            return []

    def repl(match: re.Match) -> str:
        link_text = match.group(1)
        target = match.group(2)

        # Must start with api:
        if not target.startswith("api:"):
            return match.group(0)

        # Extract the lookup key exactly as provided after 'api:'
        key = target[len("api:"):].strip()
        if not key:
            return match.group(0)

        # Build the navigation map on first use
        nav_map = _build_navigation_map()
        if not nav_map:
            return match.group(0)

        href = nav_map.get(key)
        if not href:
            # Build and print an informative warning with suggestions
            try:
                page_path = getattr(getattr(page, 'file', None), 'src_path', '<unknown>')
            except Exception:
                page_path = '<unknown>'

            print(f"Warning: Unable to resolve API link target '{key}' in '{page_path}'.")

            # Variant hints based on simple transformations
            variant_hints: list[str] = []
            dotted_variant = key.replace('//', '.').replace('/', '.')
            if dotted_variant != key:
                variant_hints.append(dotted_variant)
            slash_variant = key.replace('::', '::').replace('.', '/')  # keep prefix delimiter intact
            if slash_variant != key and '::' in key:
                # Restore the '::' that might have been affected by global replacement
                prefix, rest = key.split('::', 1)
                slash_variant = f"{prefix}::{rest.replace('.', '/')}"
                if slash_variant != key:
                    variant_hints.append(slash_variant)

            # Fuzzy suggestions from the navigation map
            suggestions = _suggest_keys(nav_map, key, limit=5)
            if suggestions:
                print("  Did you mean one of:")
                for s in suggestions:
                    print(f"    - {s}")

            if variant_hints:
                print("  Try variant forms (based on separators):")
                for v in dict.fromkeys(variant_hints):  # de-duplicate while preserving order
                    print(f"    - {v}")

            # Leave the original markdown link unchanged
            return match.group(0)

        # Transform link text: replace whitespace with hyphens
        display_text = re.sub(r"\s+", "-", link_text.strip())

        # Prefer absolute URL to avoid relative path issues
        resolved_href = urljoin(BASE_URL, href)
        return f"[{display_text}]({resolved_href})"

    return pattern.sub(repl, markdown)
