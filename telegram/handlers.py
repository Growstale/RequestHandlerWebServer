# handlers.py
from telegram import Update
from telegram.ext import ContextTypes
from telegram.constants import ParseMode
from api_client import check_backend_health


async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    await update.message.reply_html(
        f"Привет, {user.mention_html()}!\n\n"
        "Я бот для демонстрации взаимодействия с Java-бэкендом. "
        "Используйте команду /health, чтобы проверить связь с сервером."
    )


async def health_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Проверяю связь с сервером...")
    result_message = await check_backend_health()
    await update.message.reply_text(result_message, parse_mode=ParseMode.MARKDOWN)