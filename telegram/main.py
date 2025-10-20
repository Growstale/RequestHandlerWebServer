import logging
from telegram.ext import Application, CommandHandler
from config import BOT_TOKEN
from handlers import start_command, health_command

logging.basicConfig(
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    level=logging.INFO
)
logging.getLogger("httpx").setLevel(logging.WARNING)
logger = logging.getLogger(__name__)


def main():
    logger.info("Запуск бота...")

    if not BOT_TOKEN:
        logger.error("Токен бота не найден! Проверьте .env файл.")
        return

    app = Application.builder().token(BOT_TOKEN).build()

    app.add_handler(CommandHandler("start", start_command))
    app.add_handler(CommandHandler("health", health_command))

    logger.info("Бот готов к работе. Запускаю polling...")

    app.run_polling()


if __name__ == "__main__":
    main()