import logging
import asyncio
import io
from aiohttp import web
from telegram import InlineKeyboardButton, InlineKeyboardMarkup
from telegram.error import BadRequest, TelegramError
from telegram.constants import ParseMode
from telegram.ext import (
    Application, CommandHandler, ConversationHandler, CallbackQueryHandler,
    MessageHandler, filters, ContextTypes
)
from config import BOT_TOKEN
from bot_logging import logger
from handlers import (
    Context, start_command, chat_id_command, refresh_command,
    new_request_start, cancel_command,
    CREATE_SELECT_SHOP, CREATE_SELECT_CONTRACTOR, CREATE_SELECT_WORK_CATEGORY,
    CREATE_SELECT_URGENCY, CREATE_ENTER_DESCRIPTION, CREATE_ENTER_CUSTOM_DAYS,
    select_shop_callback, select_contractor_callback, select_work_category_callback,
    select_urgency_callback, description_handler, custom_days_handler,
    view_requests_start, view_menu_callback, view_search_handler,
    view_sort_callback, action_callback_handler, add_comment_handler, add_photo_handler, view_request_details,
    VIEW_MAIN_MENU, VIEW_SET_SEARCH_TERM, VIEW_SET_SORTING, VIEW_DETAILS, VIEW_ADD_COMMENT, VIEW_ADD_PHOTO,
    start_create_request, start_edit_request,
    editor_main_callback, editor_select_shop, editor_select_contractor,
    editor_select_work, editor_select_urgency, editor_select_status, editor_input_text,
    EDITOR_MAIN_MENU, EDITOR_SELECT_SHOP, EDITOR_SELECT_CONTRACTOR,
    EDITOR_SELECT_WORK, EDITOR_SELECT_URGENCY, EDITOR_INPUT_TEXT, EDITOR_SELECT_STATUS,
    start_delete_comment_handler, confirm_delete_comment_handler,
    start_delete_photo_handler, preview_delete_photo_handler, finalize_delete_photo_handler,
    DELETE_COMMENT_SELECT, DELETE_PHOTO_SELECT
)


async def http_notify_handler(request):
    try:
        data = await request.json()
        chat_id = data.get('chatId')
        text = data.get('text')

        if not chat_id or not text:
            return web.Response(status=400, text="Missing chatId or text")

        bot_app = request.app['bot_app']

        await bot_app.bot.send_message(
            chat_id=chat_id,
            text=text,
            parse_mode=ParseMode.MARKDOWN_V2
        )
        logger.info(f"Notification sent to {chat_id}")
        return web.Response(text="OK")
    except Exception as e:
        logger.error(f"Failed to process notification: {e}")
        return web.Response(status=500, text=str(e))


async def http_notify_comment_handler(request):
    try:
        data = await request.json()
        chat_id = data.get('chatId')
        text = data.get('text')
        req_id = data.get('requestId')
        comment_id = data.get('commentId')

        bot_app = request.app['bot_app']
        keyboard = [[InlineKeyboardButton("↩️ Ответить", callback_data=f"act_add_comment_{req_id}_{comment_id}")]]

        await bot_app.bot.send_message(
            chat_id=chat_id,
            text=text,
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode=ParseMode.MARKDOWN_V2
        )
        return web.Response(text="OK")
    except Exception as e:
        logger.error(f"Failed to process comment notification: {e}")
        return web.Response(status=500, text=str(e))


async def http_notify_comment_handler(request):
    try:
        data = await request.json()
        chat_id, text = data.get('chatId'), data.get('text')
        req_id, comment_id = data.get('requestId'), data.get('commentId')

        bot_app = request.app['bot_app']
        # Кнопка "Ответить", которая несет в себе ID заявки и ID родительского комментария
        keyboard = [[InlineKeyboardButton("↩️ Ответить", callback_data=f"act_add_comment_{req_id}_{comment_id}")]]

        await bot_app.bot.send_message(
            chat_id=chat_id, text=text,
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode=ParseMode.MARKDOWN_V2
        )
        return web.Response(text="OK")
    except Exception as e:
        return web.Response(status=500, text=str(e))


async def http_notify_photo_handler(request):
    try:
        reader = await request.multipart()

        chat_id = None
        caption = None
        file_data = None

        async for field in reader:
            if field.name == 'chatId':
                val = await field.read_chunk()
                chat_id = int(val.decode('utf-8'))
            elif field.name == 'caption':
                val = await field.read_chunk()
                caption = val.decode('utf-8')
            elif field.name == 'file':
                file_data = await field.read()

        if not chat_id or not file_data:
            return web.Response(status=400, text="Missing chatId or file")

        bot_app = request.app['bot_app']

        photo_file = io.BytesIO(file_data)
        photo_file.name = 'image.jpg'

        await bot_app.bot.send_photo(
            chat_id=chat_id,
            photo=photo_file,
            caption=caption,
            parse_mode=ParseMode.MARKDOWN_V2
        )
        logger.info(f"Photo notification sent to {chat_id}")
        return web.Response(text="OK")
    except BadRequest as e:
        if "Chat not found" in str(e) or "chat not found" in str(e):
            logger.warning(f"Chat {chat_id} not found: {e}")
            return web.Response(status=400, text=f"Chat not found: {chat_id}")

        logger.error(f"Telegram Bad Request for {chat_id}: {e}")
        return web.Response(status=400, text=str(e))

    except Exception as e:
        logger.error(f"Failed to process photo notification: {e}")
        return web.Response(status=500, text=str(e))


async def check_chat_handler(request):
    chat_id_str = request.match_info['chat_id']
    try:
        chat_id = int(chat_id_str)
        bot_app = request.app['bot_app']

        chat = await bot_app.bot.get_chat(chat_id)

        return web.json_response({
            "exists": True,
            "title": chat.title or chat.first_name or "Unknown"
        })

    except ValueError:
        return web.Response(status=400, text="Invalid Chat ID format")
    except BadRequest:
        return web.json_response({"exists": False}, status=404)
    except Exception as e:
        logger.error(f"Error checking chat {chat_id_str}: {e}")
        return web.Response(status=500, text=str(e))


async def main():
    logger.info("Запуск бота...")
    if not BOT_TOKEN:
        logger.error("Токен бота не найден!")
        return

    context_types = ContextTypes(context=Context)
    application = (
        Application.builder()
        .token(BOT_TOKEN)
        .context_types(context_types)
        .read_timeout(30)
        .write_timeout(30)
        .connect_timeout(30)
        .build()
    )

    create_conv = ConversationHandler(
        entry_points=[
            CommandHandler("newrequest", start_create_request),
            MessageHandler(filters.Regex("^➕ Новая заявка$"), start_create_request)
        ],
        states={
            EDITOR_MAIN_MENU: [CallbackQueryHandler(editor_main_callback, pattern="^(editor_|edit_field_)")],
            EDITOR_SELECT_SHOP: [CallbackQueryHandler(editor_select_shop, pattern="^eshop_")],
            EDITOR_SELECT_CONTRACTOR: [CallbackQueryHandler(editor_select_contractor, pattern="^econtr_")],
            EDITOR_SELECT_WORK: [CallbackQueryHandler(editor_select_work, pattern="^ework_")],
            EDITOR_SELECT_URGENCY: [CallbackQueryHandler(editor_select_urgency, pattern="^eurg_")],
            EDITOR_INPUT_TEXT: [MessageHandler(filters.TEXT & ~filters.COMMAND, editor_input_text)],
        },
        fallbacks=[CommandHandler("cancel", cancel_command)],
    )

    view_conv = ConversationHandler(
        entry_points=[
            CommandHandler("requests", view_requests_start),
            MessageHandler(filters.Regex(r'^\/[_]*(\d+)[_]*$'), view_request_details),
            MessageHandler(filters.Regex("^📋 Мои заявки$"), view_requests_start)
        ],
        states={
            VIEW_MAIN_MENU: [
                CallbackQueryHandler(view_menu_callback, pattern="^view_"),
                MessageHandler(filters.Regex(r'^\/[_]*(\d+)[_]*$'), view_request_details)
            ],
            VIEW_DETAILS: [
                CallbackQueryHandler(action_callback_handler, pattern="^act_"),
                CallbackQueryHandler(action_callback_handler, pattern="^start_del_")
            ],
            DELETE_COMMENT_SELECT: [
                CallbackQueryHandler(confirm_delete_comment_handler, pattern="^conf_del_cmt_"),
                CallbackQueryHandler(action_callback_handler, pattern="^act_")
            ],
            DELETE_PHOTO_SELECT: [
                CallbackQueryHandler(preview_delete_photo_handler, pattern="^preview_del_img_"),
                CallbackQueryHandler(finalize_delete_photo_handler, pattern="^fin_del_img_"),
                CallbackQueryHandler(action_callback_handler, pattern="^start_del_img_"),
                CallbackQueryHandler(action_callback_handler, pattern="^act_")
            ],
            VIEW_SET_SEARCH_TERM: [MessageHandler(filters.TEXT & ~filters.COMMAND, view_search_handler)],
            VIEW_SET_SORTING: [CallbackQueryHandler(view_sort_callback, pattern="^(view|sort)_")],
            VIEW_ADD_COMMENT: [MessageHandler(filters.TEXT & ~filters.COMMAND, add_comment_handler)],
            VIEW_ADD_PHOTO: [
                MessageHandler(filters.PHOTO | filters.Document.IMAGE, add_photo_handler),
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

    application.add_handler(create_conv)
    application.add_handler(view_conv)
    application.add_handler(CommandHandler("start", start_command))
    application.add_handler(CommandHandler("chatid", chat_id_command))
    application.add_handler(MessageHandler(filters.Regex("^🆔 ID Чата$"), chat_id_command))
    application.add_handler(MessageHandler(filters.Regex("^🔄 Обновить$"), refresh_command))
    application.add_handler(CallbackQueryHandler(lambda u, c: u.callback_query.answer(), pattern="^noop$"))

    await application.initialize()
    await application.start()
    await application.updater.start_polling()

    MAX_SIZE = 100 * 1024 * 1024
    server = web.Application(client_max_size=MAX_SIZE)

    server['bot_app'] = application
    server.router.add_post('/notify', http_notify_handler)
    server.router.add_post('/notify/photo', http_notify_photo_handler)
    server.router.add_get('/check/{chat_id}', check_chat_handler)
    server.router.add_post('/notify-comment', http_notify_comment_handler)

    runner = web.AppRunner(server, access_log=None)
    await runner.setup()
    site = web.TCPSite(runner, '0.0.0.0', 8081)
    await site.start()

    logger.info(f"HTTP Server started on port 8081. Max upload size: {MAX_SIZE} bytes")
    logger.info("Bot polling started")

    try:
        await asyncio.Event().wait()
    except KeyboardInterrupt:
        pass
    finally:
        await application.updater.stop()
        await application.stop()
        await application.shutdown()
        await runner.cleanup()


if __name__ == "__main__":
    import sys

    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(main())