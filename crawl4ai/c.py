import asyncio
import email
import email.policy
from pathlib import Path

from crawl4ai import AsyncWebCrawler, CacheMode
from crawl4ai.async_configs import BrowserConfig, CrawlerRunConfig
from crawl4ai.markdown_generation_strategy import DefaultMarkdownGenerator


def html_from_mhtml(path: Path) -> str:
    """
    Peel the MIME envelope off an .mhtml file and return the first
    text/html part, already decoded (handles quoted-printable / base-64).
    """
    with path.open("rb") as fp:
        msg = email.message_from_binary_file(fp, policy=email.policy.default)

    for part in msg.walk():
        if part.get_content_type() == "text/html":
            data = part.get_payload(decode=True)
            charset = part.get_content_charset() or "utf-8"
            return data.decode(charset, errors="replace")

    raise ValueError("No text/html part found in the MHTML file")


async def main() -> None:
    mhtml_path = Path("data/test.mhtml").resolve()
    raw_html = html_from_mhtml(mhtml_path)

    # Strip link-reference list and keep content *inside* `.body`
    md_generator = DefaultMarkdownGenerator(
        options={
            "ignore_links": False,   # keep anchor text
            "use_footnotes": False,  # stop html2text from pushing refs to the end
            "body_width": 0,         # no hard wrap
        }
    )

    run_cfg = CrawlerRunConfig(
        cache_mode=CacheMode.BYPASS,
        verbose=True,
        word_count_threshold=10,
        capture_console_messages=True,
        css_selector=".body > p:not([class]):not([id]):not([style]:not([data-attrs])",
        excluded_selector="div .post-ufi , .single-post-section, .modal, #discussion, [data-testid='navbar'], [data-component-name='SubscribeWidget'] ~ *",
        only_text=True,                # collapse to plain text blocks
        markdown_generator=md_generator,   # suppress link-reference block :contentReference[oaicite:1]{index=1}
    )

    async with AsyncWebCrawler(config=BrowserConfig()) as crawler:
        result = await crawler.arun(url=f"raw:{raw_html}", config=run_cfg)

    # `result.markdown` is a MarkdownGenerationResult; grab the raw part.
    md_obj = result.markdown
    plain_text = md_obj.raw_markdown if hasattr(md_obj, "raw_markdown") else str(md_obj)
    print(plain_text.strip())


if __name__ == "__main__":
    asyncio.run(main())
