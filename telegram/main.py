import logging
from telegram.ext import (
    Application, CommandHandler, ConversationHandler, CallbackQueryHandler,
    MessageHandler, filters, ContextTypes
)
from config import BOT_TOKEN
from bot_logging import logger
from handlers import (
    Context, start_command, chat_id_command,
    # Создание
    new_request_start, cancel_command,
    CREATE_SELECT_SHOP, CREATE_SELECT_CONTRACTOR, CREATE_SELECT_WORK_CATEGORY,
    CREATE_SELECT_URGENCY, CREATE_ENTER_DESCRIPTION, CREATE_ENTER_CUSTOM_DAYS,
    select_shop_callback, select_contractor_callback, select_work_category_callback,
    select_urgency_callback, description_handler, custom_days_handler,
    # Просмотр и действия
    view_requests_start, view_menu_callback, view_search_handler,
    view_sort_callback, action_callback_handler, add_comment_handler, add_photo_handler, view_request_details,
    VIEW_MAIN_MENU, VIEW_SET_SEARCH_TERM, VIEW_SET_SORTING, VIEW_DETAILS, VIEW_ADD_COMMENT, VIEW_ADD_PHOTO,
    start_create_request, start_edit_request,
    editor_main_callback, editor_select_shop, editor_select_contractor,
    editor_select_work, editor_select_urgency, editor_select_status, editor_input_text,
    EDITOR_MAIN_MENU, EDITOR_SELECT_SHOP, EDITOR_SELECT_CONTRACTOR,
    EDITOR_SELECT_WORK, EDITOR_SELECT_URGENCY, EDITOR_INPUT_TEXT, EDITOR_SELECT_STATUS
)


def main():
    logger.info("Запуск бота...")
    if not BOT_TOKEN:
        logger.error("Токен бота не найден!")
        return

    context_types = ContextTypes(context=Context)
    app = Application.builder().token(BOT_TOKEN).context_types(context_types).build()

    # --- Диалог создания заявки (без изменений) ---
    create_conv = ConversationHandler(
        entry_points=[CommandHandler("newrequest", start_create_request)],
        states={
            EDITOR_MAIN_MENU: [CallbackQueryHandler(editor_main_callback, pattern="^(editor_|edit_field_)")],
            EDITOR_SELECT_SHOP: [CallbackQueryHandler(editor_select_shop, pattern="^eshop_")],
            EDITOR_SELECT_CONTRACTOR: [CallbackQueryHandler(editor_select_contractor, pattern="^econtr_")],
            EDITOR_SELECT_WORK: [CallbackQueryHandler(editor_select_work, pattern="^ework_")],
            EDITOR_SELECT_URGENCY: [CallbackQueryHandler(editor_select_urgency, pattern="^eurg_")],
            EDITOR_INPUT_TEXT: [MessageHandler(filters.TEXT & ~filters.COMMAND, editor_input_text)],
            # EDITOR_SELECT_STATUS не нужен при создании
        },
        fallbacks=[CommandHandler("cancel", cancel_command)],
    )

    # --- ЕДИНЫЙ ДИАЛОГ ДЛЯ ПРОСМОТРА И ДЕЙСТВИЙ ---
    view_conv = ConversationHandler(
        entry_points=[
            CommandHandler("requests", view_requests_start),
            MessageHandler(filters.Regex(r'^\/[_]*(\d+)[_]*$'), view_request_details)
        ],
        states={
            # Главное меню списка заявок
            VIEW_MAIN_MENU: [
                CallbackQueryHandler(view_menu_callback, pattern="^view_"),
                MessageHandler(filters.Regex(r'^\/[_]*(\d+)[_]*$'), view_request_details)
            ],
            # Меню детального просмотра заявки
            VIEW_DETAILS: [
                CallbackQueryHandler(action_callback_handler, pattern="^act_")
            ],
            # Вложенные состояния для действий
            VIEW_SET_SEARCH_TERM: [MessageHandler(filters.TEXT & ~filters.COMMAND, view_search_handler)],
            VIEW_SET_SORTING: [CallbackQueryHandler(view_sort_callback, pattern="^(view|sort)_")],
            VIEW_ADD_COMMENT: [MessageHandler(filters.TEXT & ~filters.COMMAND, add_comment_handler)],
            VIEW_ADD_PHOTO: [
                # Обработчик фото
                MessageHandler(filters.PHOTO | filters.Document.IMAGE, add_photo_handler),
                # ИСПРАВЛЕНИЕ: Добавлен обработчик кнопок (для кнопки "Назад", которая появится после загрузки)
                CallbackQueryHandler(action_callback_handler, pattern="^act_")
            ],
            EDITOR_MAIN_MENU: [CallbackQueryHandler(editor_main_callback, pattern="^(editor_|edit_field_)")],
            EDITOR_SELECT_SHOP: [CallbackQueryHandler(editor_select_shop, pattern="^eshop_")],
            EDITOR_SELECT_CONTRACTOR: [CallbackQueryHandler(editor_select_contractor, pattern="^econtr_")],
            EDITOR_SELECT_WORK: [CallbackQueryHandler(editor_select_work, pattern="^ework_")],
            EDITOR_SELECT_URGENCY: [CallbackQueryHandler(editor_select_urgency, pattern="^eurg_")],
            EDITOR_SELECT_STATUS: [CallbackQueryHandler(editor_select_status, pattern="^estatus_")],
            EDITOR_INPUT_TEXT: [MessageHandler(filters.TEXT & ~filters.COMMAND, editor_input_text)],
        },
        fallbacks=[CommandHandler("cancel", cancel_command)],
        name="view_conversation",
        persistent=False,
        allow_reentry=True
    )

    app.add_handler(create_conv)
    app.add_handler(view_conv)

    # --- Глобальные обработчики ---
    app.add_handler(CommandHandler("start", start_command))
    app.add_handler(CommandHandler("chatid", chat_id_command))

    app.add_handler(CallbackQueryHandler(lambda u, c: u.callback_query.answer(), pattern="^noop$"))

    logger.info("Бот готов к работе. Запускаю polling...")
    app.run_polling()


if __name__ == "__main__":
    main()