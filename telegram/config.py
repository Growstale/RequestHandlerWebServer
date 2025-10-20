import os
from dotenv import load_dotenv

load_dotenv()

BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
API_KEY = os.getenv("BACKEND_API_KEY")
BACKEND_URL = os.getenv("BACKEND_URL")

if not all([BOT_TOKEN, API_KEY, BACKEND_URL]):
    raise ValueError(
        "Ошибка: одна или несколько переменных окружения не установлены. "
        "Проверьте ваш .env файл (TELEGRAM_BOT_TOKEN, BACKEND_API_KEY, BACKEND_URL)."
    )