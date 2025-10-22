import logging
from telegram.ext import (
    Application,
    CommandHandler,
    ConversationHandler,
    CallbackQueryHandler,
    MessageHandler,
    filters
)
from config import BOT_TOKEN
from handlers import (
    start_command,
    chat_id_command,
    new_request_start,
    select_shop_callback,
    select_contractor_callback,
    select_work_category_callback,
    select_urgency_callback,
    description_handler,
    custom_days_handler,
    cancel_command,
    SELECT_SHOP, SELECT_CONTRACTOR, SELECT_WORK_CATEGORY,
    SELECT_URGENCY, ENTER_DESCRIPTION, ENTER_CUSTOM_DAYS
)

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

    conv_handler = ConversationHandler(
        entry_points=[CommandHandler("newrequest", new_request_start)],
        states={
            SELECT_SHOP: [CallbackQueryHandler(select_shop_callback, pattern="^shop_")],
            SELECT_CONTRACTOR: [CallbackQueryHandler(select_contractor_callback, pattern="^contractor_")],
            SELECT_WORK_CATEGORY: [CallbackQueryHandler(select_work_category_callback, pattern="^work_")],
            SELECT_URGENCY: [CallbackQueryHandler(select_urgency_callback, pattern="^urgency_")],
            ENTER_DESCRIPTION: [MessageHandler(filters.TEXT & ~filters.COMMAND, description_handler)],
            ENTER_CUSTOM_DAYS: [MessageHandler(filters.TEXT & ~filters.COMMAND, custom_days_handler)],
        },
        fallbacks=[CommandHandler("cancel", cancel_command)],
    )

    app.add_handler(conv_handler)
    app.add_handler(CommandHandler("start", start_command))
    app.add_handler(CommandHandler("chatid", chat_id_command))
    app.add_handler(CallbackQueryHandler(lambda u, c: u.callback_query.answer(), pattern="^noop$"))


    logger.info("Бот готов к работе. Запускаю polling...")
    app.run_polling()

if __name__ == "__main__":
    main()