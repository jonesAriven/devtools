FROM alpine:latest

RUN apk add --no-cache python3 py3-pip && \
    python3 -m venv /venv && \
    /venv/bin/pip install --no-cache-dir flask gunicorn

WORKDIR /app

COPY memory-panel.py .
COPY config.yaml .
COPY templates/ templates/
COPY static/ static/

EXPOSE 8720

CMD ["/venv/bin/gunicorn", "--bind", "0.0.0.0:8720", "--workers", "2", "--timeout", "60", "memory-panel:app"]