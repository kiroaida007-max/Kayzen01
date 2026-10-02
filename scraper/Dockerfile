# WAVE data pipeline: Scrapy + Playwright (Chromium) + Firecrawl adapter + AIS forwarder.
FROM python:3.12-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

WORKDIR /app
COPY requirements.txt ./
RUN pip install -r requirements.txt \
 && python -m playwright install --with-deps chromium \
 && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml scrapy.cfg ./
COPY wave_scraper ./wave_scraper
RUN pip install --no-deps .

# Never run a browser as root.
RUN useradd --create-home --uid 10001 wave && mkdir -p /app/reports && chown -R wave /app/reports
USER wave

# Crawls are short-lived jobs (Kubernetes CronJobs); the AIS forwarder is a long-running Deployment.
ENTRYPOINT ["wave-scraper"]
CMD ["crawl"]
