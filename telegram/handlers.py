from telegram import Update
from telegram.ext import ConversationHandler, CallbackContext
from telegram.constants import ParseMode, ChatType
import api_client
from utils import create_paginated_keyboard


(SELECT_SHOP, SELECT_CONTRACTOR, SELECT_WORK_CATEGORY,
 SELECT_URGENCY, ENTER_DESCRIPTION, ENTER_CUSTOM_DAYS) = range(6)


async def new_request_start(update: Update, context: CallbackContext) -> int:
    user_id = update.effective_user.id
    chat_type = update.message.chat.type

    user_data = await api_client.get_user_by_telegram_id(user_id)
    if not user_data or user_data.get("roleName") != "RetailAdmin":
        await update.message.reply_text("❌ У вас нет прав для создания заявок.")
        return ConversationHandler.END

    context.user_data['creator_db_id'] = user_data['userID']
    context.user_data['request_data'] = {}

    if chat_type in [ChatType.GROUP, ChatType.SUPERGROUP]:
        chat_id = update.message.chat.id
        chat_info = await api_client.get_chat_info_by_telegram_id(chat_id)

        if chat_info:
            context.user_data['request_data']['shopID'] = chat_info['shopID']
            context.user_data['request_data']['assignedContractorID'] = chat_info['contractorID']
            await update.message.reply_text(
                f"Заявка для магазина \"{chat_info['shopName']}\" и подрядчика \"{chat_info['contractorLogin']}\"")
            return await ask_work_category(update, context)
        else:
            await update.message.reply_text(
                "❌ Этот чат не привязан к магазину и подрядчику. Создание заявки отсюда невозможно.")
            return ConversationHandler.END
    else:
        return await ask_shop(update, context)


async def cancel_command(update: Update, context: CallbackContext) -> int:
    await update.message.reply_text("Создание заявки отменено.", reply_markup=None)
    context.user_data.clear()
    return ConversationHandler.END



async def ask_shop(update: Update, context: CallbackContext) -> int:
    shops_response = await api_client.get_all_shops()
    if not shops_response or not shops_response.get('content'):
        await update.message.reply_text("Не удалось загрузить список магазинов.")
        return ConversationHandler.END

    context.user_data['shops'] = shops_response['content']
    keyboard = create_paginated_keyboard(context.user_data['shops'], 0, 'shop', 'shopName', 'shopID')
    await update.message.reply_text("<b>Шаг 1/5:</b> Выберите магазин:", reply_markup=keyboard,
                                    parse_mode=ParseMode.HTML)
    return SELECT_SHOP


async def select_shop_callback(update: Update, context: CallbackContext) -> int | None:
    query = update.callback_query
    await query.answer()

    action, value = query.data.split('_', 2)[1:]

    if action == 'page':
        page = int(value)
        keyboard = create_paginated_keyboard(context.user_data['shops'], page, 'shop', 'shopName', 'shopID')
        await query.edit_message_text("<b>Шаг 1/5:</b> Выберите магазин:", reply_markup=keyboard,
                                      parse_mode=ParseMode.HTML)
        return SELECT_SHOP

    elif action == 'select':
        shop_id = int(value)
        shop = next((s for s in context.user_data['shops'] if s['shopID'] == shop_id), None)
        context.user_data['request_data']['shopID'] = shop_id
        await query.edit_message_text(f"Выбран магазин: <b>{shop['shopName']}</b>", parse_mode=ParseMode.HTML)
        return await ask_contractor(update, context)
    return None


async def ask_contractor(update: Update, context: CallbackContext) -> int:
    contractors = await api_client.get_all_contractors()
    if not contractors:
        await update.effective_message.reply_text("Не удалось загрузить список подрядчиков.")
        return ConversationHandler.END

    context.user_data['contractors'] = contractors
    keyboard = create_paginated_keyboard(context.user_data['contractors'], 0, 'contractor', 'login', 'userID')
    await context.bot.send_message(update.effective_chat.id, "<b>Шаг 2/5:</b> Выберите подрядчика:",
                                   reply_markup=keyboard, parse_mode=ParseMode.HTML)
    return SELECT_CONTRACTOR


async def select_contractor_callback(update: Update, context: CallbackContext) -> int | None:
    query = update.callback_query
    await query.answer()

    action, value = query.data.split('_', 2)[1:]

    if action == 'page':
        page = int(value)
        keyboard = create_paginated_keyboard(context.user_data['contractors'], page, 'contractor', 'login', 'userID')
        await query.edit_message_text("<b>Шаг 2/5:</b> Выберите подрядчика:", reply_markup=keyboard,
                                      parse_mode=ParseMode.HTML)
        return SELECT_CONTRACTOR

    elif action == 'select':
        contractor_id = int(value)
        contractor = next((c for c in context.user_data['contractors'] if c['userID'] == contractor_id), None)
        context.user_data['request_data']['assignedContractorID'] = contractor_id
        await query.edit_message_text(f"Выбран подрядчик: <b>{contractor['login']}</b>", parse_mode=ParseMode.HTML)
        return await ask_work_category(update, context)
    return None


async def ask_work_category(update: Update, context: CallbackContext) -> int:
    work_cats_response = await api_client.get_all_work_categories()
    if not work_cats_response or not work_cats_response.get('content'):
        await update.effective_message.reply_text("Не удалось загрузить виды работ.")
        return ConversationHandler.END

    context.user_data['work_categories'] = work_cats_response['content']
    keyboard = create_paginated_keyboard(context.user_data['work_categories'], 0, 'work', 'workCategoryName',
                                         'workCategoryID')
    await context.bot.send_message(update.effective_chat.id, "<b>Шаг 3/5:</b> Выберите вид работ:",
                                   reply_markup=keyboard, parse_mode=ParseMode.HTML)
    return SELECT_WORK_CATEGORY


async def select_work_category_callback(update: Update, context: CallbackContext) -> int | None:
    query = update.callback_query
    await query.answer()

    action, value = query.data.split('_', 2)[1:]

    if action == 'page':
        page = int(value)
        keyboard = create_paginated_keyboard(context.user_data['work_categories'], page, 'work', 'workCategoryName',
                                             'workCategoryID')
        await query.edit_message_text("<b>Шаг 3/5:</b> Выберите вид работ:", reply_markup=keyboard,
                                      parse_mode=ParseMode.HTML)
        return SELECT_WORK_CATEGORY

    elif action == 'select':
        work_cat_id = int(value)
        work_cat = next((w for w in context.user_data['work_categories'] if w['workCategoryID'] == work_cat_id), None)
        context.user_data['request_data']['workCategoryID'] = work_cat_id
        await query.edit_message_text(f"Выбран вид работ: <b>{work_cat['workCategoryName']}</b>",
                                      parse_mode=ParseMode.HTML)
        return await ask_urgency(update, context)
    return None


async def ask_urgency(update: Update, context: CallbackContext) -> int:
    urgencies = await api_client.get_all_urgency_categories()
    if not urgencies:
        await update.effective_message.reply_text("Не удалось загрузить категории срочности.")
        return ConversationHandler.END

    context.user_data['urgencies'] = urgencies
    keyboard = create_paginated_keyboard(context.user_data['urgencies'], 0, 'urgency', 'urgencyName', 'urgencyID')
    await context.bot.send_message(update.effective_chat.id, "<b>Шаг 4/5:</b> Выберите срочность:",
                                   reply_markup=keyboard, parse_mode=ParseMode.HTML)
    return SELECT_URGENCY


async def select_urgency_callback(update: Update, context: CallbackContext) -> int | None:
    query = update.callback_query
    await query.answer()

    action, value = query.data.split('_', 2)[1:]

    if action == 'page':
        page = int(value)
        keyboard = create_paginated_keyboard(context.user_data['urgencies'], page, 'urgency', 'urgencyName',
                                             'urgencyID')
        await query.edit_message_text("<b>Шаг 4/5:</b> Выберите срочность:", reply_markup=keyboard,
                                      parse_mode=ParseMode.HTML)
        return SELECT_URGENCY

    elif action == 'select':
        urgency_id = int(value)
        urgency = next((u for u in context.user_data['urgencies'] if u['urgencyID'] == urgency_id), None)
        context.user_data['request_data']['urgencyID'] = urgency_id
        context.user_data['is_customizable'] = urgency['urgencyName'] == 'Customizable'

        await query.edit_message_text(f"Выбрана срочность: <b>{urgency['urgencyName']}</b>", parse_mode=ParseMode.HTML)

        await context.bot.send_message(
            update.effective_chat.id,
            "<b>Шаг 5/5:</b> Теперь введите подробное описание заявки.",
            parse_mode=ParseMode.HTML
        )

        return ENTER_DESCRIPTION
    return None


async def description_handler(update: Update, context: CallbackContext) -> int:
    description = update.message.text
    context.user_data['request_data']['description'] = description

    if context.user_data.get('is_customizable'):
        await update.message.reply_text(
            "Срочность 'Настраиваемая'. Введите количество дней на выполнение (например, 10)."
        )
        return ENTER_CUSTOM_DAYS
    else:
        return await submit_request(update, context)


async def custom_days_handler(update: Update, context: CallbackContext) -> int:
    days = update.message.text
    if not days.isdigit() or not 1 <= int(days) <= 365:
        await update.message.reply_text("❌ Неверное значение. Введите число от 1 до 365.")
        return ENTER_CUSTOM_DAYS

    context.user_data['request_data']['customDays'] = int(days)
    return await submit_request(update, context)


async def chat_id_command(update: Update, context: CallbackContext):
    chat_id = update.message.chat.id
    message_text = (
        f"Информация о чате:\n"
        f"📝 **Название:** {update.message.chat.title}\n"
        f"🆔 **ID Чата:** `{chat_id}`\n\n"
        f"Используйте этот ID при настройке связей в админ-панели."
    )
    await update.message.reply_text(message_text, parse_mode=ParseMode.MARKDOWN)


async def submit_request(update: Update, context: CallbackContext) -> int:
    await update.effective_message.reply_text("Отправляю данные на сервер...")

    payload = {
        "description": context.user_data['request_data']['description'],
        "shopID": context.user_data['request_data']['shopID'],
        "workCategoryID": context.user_data['request_data']['workCategoryID'],
        "urgencyID": context.user_data['request_data']['urgencyID'],
        "assignedContractorID": context.user_data['request_data']['assignedContractorID'],
        "createdByUserID": context.user_data['creator_db_id']
    }
    if 'customDays' in context.user_data['request_data']:
        payload['customDays'] = context.user_data['request_data']['customDays']

    response = await api_client.create_request(payload)

    if response and response.get('requestID'):
        await update.effective_message.reply_text(f"✅ Заявка успешно создана! ID новой заявки: {response['requestID']}")
    else:
        await update.effective_message.reply_text(
            "❌ Не удалось создать заявку. Попробуйте снова или обратитесь к администратору.")

    context.user_data.clear()
    return ConversationHandler.END


async def start_command(update: Update, context: CallbackContext):
    user = update.effective_user
    await update.message.reply_html(
        f"Привет, {user.mention_html()}!\n\n"
        "Используйте команду /newrequest для создания новой заявки (только для администраторов).\n"
        "Используйте /health для проверки связи с сервером."
    )