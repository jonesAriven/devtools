FROM python:3.11-slim

WORKDIR /app

RUN pip install --no-cache-dir flask gunicorn

COPY memory-panel.py .
COPY config.yaml .
COPY templates/ templates/
COPY static/ static/

EXPOSE 8720

CMD ["gunicorn", "--bind", "0.0.0.0:8720", "--workers", "2", "--timeout", "60", "memory-panel:app"]