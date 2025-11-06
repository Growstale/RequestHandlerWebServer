from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup, InputMediaPhoto
import datetime

from typing import Dict, Any, Coroutine
from telegram import Update
from telegram.ext import ContextTypes, ConversationHandler, CallbackContext, ExtBot
from telegram.constants import ParseMode, ChatType
import api_client
from utils import create_paginated_keyboard
from bot_logging import logger


class CustomContext(CallbackContext[ExtBot, Dict, Dict, Dict]):
    """Кастомный класс контекста для строгой типизации."""
    @classmethod
    def from_update(cls, update: object, application: object) -> "CustomContext":
        return cls(application=application, chat_id=update.effective_chat.id, user_id=update.effective_user.id)

Context = CustomContext


(CREATE_SELECT_SHOP, CREATE_SELECT_CONTRACTOR, CREATE_SELECT_WORK_CATEGORY,
 CREATE_SELECT_URGENCY, CREATE_ENTER_DESCRIPTION, CREATE_ENTER_CUSTOM_DAYS) = range(6)

(VIEW_MAIN_MENU, VIEW_SET_SEARCH_TERM, VIEW_SET_SORTING, VIEW_DETAILS,
 VIEW_COMMENT_LIST, VIEW_ADD_COMMENT, VIEW_PHOTO_LIST) = range(6, 13)


def escape_markdown(text: str) -> str:
    if not isinstance(text, str):
        return ""
    escape_chars = r'_*[]()~`>#+-=|{}.!-'
    return "".join(f"\\{char}" if char in escape_chars else char for char in text)


def format_request_list_item(req: dict) -> str:
    status_icon = "🟢" if req['status'] == 'Done' else ("⚪️" if req['status'] == 'In work' else "⚫️")
    overdue_icon = "❗️" if req['isOverdue'] else ""

    shop_name = escape_markdown(req['shopName'])
    description = escape_markdown(req['description'][:50])

    return f"{status_icon} /_{req['requestID']}_: {shop_name} {overdue_icon}\n_{description}\\.\\.\\._"


def format_request_details(req: dict) -> str:
    created_at_dt = datetime.datetime.fromisoformat(req['createdAt'])
    created_at = created_at_dt.strftime('%d.%m.%Y %H:%M')
    escaped_created_at = escape_markdown(created_at)

    deadline_info = ""
    if req['daysRemaining'] is not None:
        # Сначала преобразуем число в строку, а затем экранируем его
        days_remaining_str = escape_markdown(str(req['daysRemaining']))
        deadline_info = f"{days_remaining_str} дн\\."
    else:
        # Этот символ — длинное тире (em-dash), а не дефис, он безопасен.
        deadline_info = "—"

    if req['isOverdue']:
        # Все литералы здесь уже экранированы, а переменная теперь безопасна
        deadline_info = f"Просрочено\\! \\({deadline_info}\\)"

    executor = escape_markdown(req['assignedContractorName'] or 'Не назначен')

    # На всякий случай экранируем и 'daysForTask'
    days_for_task_str = escape_markdown(str(req['daysForTask']))

    text = (
        f"📝 *Заявка \\#{req['requestID']}*\n\n"
        f"*Магазин:* {escape_markdown(req['shopName'])}\n"
        f"*Исполнитель:* {executor}\n"
        f"*Вид работ:* {escape_markdown(req['workCategoryName'])}\n"
        f"*Срочность:* {escape_markdown(req['urgencyName'])} \\({days_for_task_str} дн\\.\\)\n"
        f"*Статус:* {escape_markdown(req['status'])}\n"
        f"*Создана:* {escaped_created_at}\n"
        f"*Срок:* {deadline_info}\n\n"
        f"*Описание:*\n```\n{escape_markdown(req['description'])}\n```"
    )
    return text


async def view_requests_start(update: Update, context: Context) -> int:
    user_id = update.effective_user.id
    user_info = await api_client.get_user_by_telegram_id(user_id)
    if not user_info:
        await update.message.reply_text("❌ Ваш Telegram ID не найден в системе.")
        return ConversationHandler.END

    context.user_data['view_filters'] = {'archived': False, 'page': 0, 'sort': ['requestID,desc']}
    context.user_data['user_info'] = user_info

    placeholder_message = await update.message.reply_text("🔄 Загружаю список заявок...")
    context.user_data['main_message_id'] = placeholder_message.message_id

    return await render_main_view_menu(update, context)



async def render_main_view_menu(update: Update, context: Context, is_callback: bool = False) -> int:
    user_id = update.effective_user.id
    filters = context.user_data.get('view_filters', {})
    response = await api_client.get_requests(user_id, filters)
    if response is None:
        # Если не удалось получить данные, лучше отправить сообщение об ошибке
        # и остаться в том же состоянии.
        error_text = "❌ Не удалось загрузить список заявок. Попробуйте позже."
        if is_callback:
            await update.callback_query.edit_message_text(error_text)
        else:
            await context.bot.send_message(update.effective_chat.id, error_text)
        return VIEW_MAIN_MENU

    requests = response.get('content', [])
    filter_lines = []
    if filters.get('archived'): filter_lines.append("Тип: Архив")
    if filters.get('searchTerm'): filter_lines.append(f"Поиск: '{escape_markdown(filters['searchTerm'])}'")
    sort_map = {'requestID,desc': 'ID ⬇️', 'requestID,asc': 'ID ⬆️', 'daysRemaining,desc': 'Срок ⬇️',
                'daysRemaining,asc': 'Срок ⬆️'}
    current_sort = filters.get('sort', ['requestID,desc'])[0]
    filter_lines.append(f"Сортировка: {sort_map.get(current_sort, current_sort)}")

    filter_text = "\n".join(filter_lines)
    message_text = f"⚙️ *Активные фильтры:*\n{filter_text}\n\n"
    if not requests:
        message_text += "_Заявок по вашим фильтрам не найдено\\._"
    else:
        message_text += "\n\n".join(format_request_list_item(req) for req in requests)

    page = response.get('currentPage', 0)
    total_pages = response.get('totalPages', 0)
    nav_row = []
    if page > 0: nav_row.append(InlineKeyboardButton("⬅️", callback_data="view_page_prev"))
    if total_pages > 1: nav_row.append(InlineKeyboardButton(f"{page + 1}/{total_pages}", callback_data="noop"))
    if page < total_pages - 1: nav_row.append(InlineKeyboardButton("➡️", callback_data="view_page_next"))

    keyboard = [[
        InlineKeyboardButton("🔎 Поиск", callback_data="view_search"),
        InlineKeyboardButton("📊 Сортировка", callback_data="view_sort"),
        InlineKeyboardButton("🗂 Архив" if not filters.get('archived') else "📂 Активные",
                             callback_data="view_toggle_archive"),
    ], [InlineKeyboardButton("🔄 Сброс", callback_data="view_reset")], nav_row,
        [InlineKeyboardButton("❌ Закрыть", callback_data="view_exit")]]

    reply_markup = InlineKeyboardMarkup(keyboard)
    try:
        # Теперь, вместо флага is_callback, мы проверяем, есть ли у нас ID сообщения для редактирования
        if context.user_data.get('main_message_id'):
            await context.bot.edit_message_text(
                text=message_text,
                chat_id=update.effective_chat.id,
                message_id=context.user_data['main_message_id'],
                reply_markup=reply_markup,
                parse_mode=ParseMode.MARKDOWN_V2
            )
        else:
            # Этот блок сработает, только если ID сообщения не был сохранен
            sent_message = await context.bot.send_message(
                chat_id=update.effective_chat.id,
                text=message_text,
                reply_markup=reply_markup,
                parse_mode=ParseMode.MARKDOWN_V2
            )
            context.user_data['main_message_id'] = sent_message.message_id

    except Exception as e:
        logger.error(f"Ошибка отправки сообщения Markdown: {e}\nТекст: {message_text}")
        # Универсальный способ отправить сообщение об ошибке
        await context.bot.send_message(
            chat_id=update.effective_chat.id,
            text="Произошла ошибка форматирования или отображения."
        )
    return VIEW_MAIN_MENU



async def view_menu_callback(update: Update, context: Context) -> int:
    query = update.callback_query
    await query.answer()
    action = query.data.split('_', 1)[1]
    filters = context.user_data.get('view_filters', {})

    if action == 'exit':
        await query.delete_message()
        context.user_data.clear()
        return ConversationHandler.END
    elif action == 'page_prev':
        filters['page'] = max(0, filters.get('page', 0) - 1)
    elif action == 'page_next':
        filters['page'] += 1
    elif action == 'toggle_archive':
        filters['archived'] = not filters.get('archived', False)
        filters['page'] = 0
    elif action == 'reset':
        context.user_data['view_filters'] = {'archived': False, 'page': 0, 'sort': ['requestID,desc']}
    elif action == 'search':
        await query.edit_message_text("Введите текст для поиска по описанию заявки:")
        return VIEW_SET_SEARCH_TERM
    elif action == 'sort':
        await query.edit_message_text("Выберите поле для сортировки:", reply_markup=get_sort_keyboard())
        return VIEW_SET_SORTING

    await render_main_view_menu(update, context, is_callback=True)
    return VIEW_MAIN_MENU


def get_sort_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup([[
        InlineKeyboardButton("ID ⬇️", callback_data="view_sort_requestID_desc"),
        InlineKeyboardButton("ID ⬆️", callback_data="view_sort_requestID_asc"),
    ], [
        InlineKeyboardButton("Срок ⬇️", callback_data="view_sort_daysRemaining_desc"),
        InlineKeyboardButton("Срок ⬆️", callback_data="view_sort_daysRemaining_asc"),
    ], [InlineKeyboardButton("◀️ Назад", callback_data="view_back_main")]])


async def view_sort_callback(update: Update, context: Context) -> int:
    query = update.callback_query
    await query.answer()
    if query.data == "view_back_main":
        return await render_main_view_menu(update, context, is_callback=True)

    field, direction = query.data.split('_')[2:]
    filters = context.user_data.get('view_filters', {})
    filters['sort'] = [f"{field},{direction}"]
    filters['page'] = 0
    await query.edit_message_text("🔄 Применяю сортировку...")
    return await render_main_view_menu(update, context, is_callback=True)


async def view_search_handler(update: Update, context: Context) -> int:
    filters = context.user_data.get('view_filters', {})
    filters['searchTerm'] = update.message.text
    filters['page'] = 0
    await update.message.delete()
    return await render_main_view_menu(update, context)


async def view_request_details(update: Update, context: Context) -> int | None:
    request_id_str = update.message.text.lstrip('/_').rstrip('_')
    if not request_id_str.isdigit():
        return VIEW_MAIN_MENU
    request_id = int(request_id_str)

    user_id = update.effective_user.id
    user_info = context.user_data.get('user_info') or await api_client.get_user_by_telegram_id(user_id)
    if not user_info:
        await update.message.reply_text("❌ Ваш Telegram ID не найден в системе.")
        return

    request_details = await api_client.get_request_details(user_id, request_id)
    if not request_details:
        await update.message.reply_text(
            f"❌ Не удалось найти заявку \\#{request_id} или у вас нет прав на ее просмотр\\.",
            parse_mode=ParseMode.MARKDOWN_V2)
        return

    context.user_data['current_request_id'] = request_id
    context.user_data['current_request_details'] = request_details
    message_text = format_request_details(request_details)

    keyboard = []
    role, status = user_info.get('roleName'), request_details.get('status')

    action_row = []
    if request_details.get('commentCount', 0) > 0:
        action_row.append(InlineKeyboardButton(f"💬 Комментарии ({request_details['commentCount']})",
                                               callback_data=f"act_comments_{request_id}"))
    if request_details.get('photoCount', 0) > 0:
        action_row.append(InlineKeyboardButton(f"🖼️ Фото ({request_details['photoCount']})",
                                               callback_data=f"act_photos_{request_id}"))
    if action_row: keyboard.append(action_row)

    second_action_row = []
    if role in ['RetailAdmin', 'Contractor'] and status != 'Closed':
        second_action_row.append(InlineKeyboardButton("➕ Комментарий", callback_data=f"act_add_comment_{request_id}"))
    if role == 'Contractor' and status == 'In work':
        second_action_row.append(InlineKeyboardButton("✅ Завершить", callback_data=f"act_complete_{request_id}"))
    if second_action_row: keyboard.append(second_action_row)

    keyboard.append([InlineKeyboardButton("◀️ Назад к списку", callback_data="act_back_list")])

    await update.message.reply_text(message_text, reply_markup=InlineKeyboardMarkup(keyboard),
                                    parse_mode=ParseMode.MARKDOWN_V2)
    return VIEW_DETAILS


async def action_callback_handler(update: Update, context: Context) -> int | None:
    query = update.callback_query
    await query.answer()

    parts = query.data.split('_')
    action = "_".join(parts[1:-1]) if len(parts) > 2 else parts[1]
    value = parts[-1] if len(parts) > 1 else None

    if action == 'back' and value == 'list':
        await query.delete_message()
        return await render_main_view_menu(update, context, is_callback=False)

    elif action == 'back' and value == 'details':
        await query.delete_message()

        class FakeUpdate:
            class FakeMessage:
                text = f"/{value}"

            message = FakeMessage()
            effective_user = query.from_user

        return await view_request_details(FakeUpdate(), context)

    elif action == 'complete':
        await complete_request_action(query, context, int(value))
        return VIEW_DETAILS  # Остаемся в том же состоянии

    elif action == 'comments':
        await show_comments(query, context, int(value))
        return VIEW_DETAILS  # Остаемся в том же состоянии

    elif action == 'photos':
        await show_photos(query, context, int(value))
        return VIEW_DETAILS  # Остаемся в том же состоянии

    elif action == 'add_comment':
        await query.edit_message_text("Введите текст вашего комментария:")
        context.user_data['current_request_id'] = int(value)
        return VIEW_ADD_COMMENT  # Переходим в состояние ввода комментария
    return None


async def complete_request_action(query, context, request_id):
    await query.edit_message_text(f"Завершаю заявку \\#{request_id}\\.\\.\\.", parse_mode=ParseMode.MARKDOWN_V2)
    response = await api_client.complete_request(query.from_user.id, request_id)
    if response:
        await query.edit_message_text(f"✅ Заявка \\#{request_id} успешно завершена\\.",
                                      parse_mode=ParseMode.MARKDOWN_V2)
    else:
        await query.edit_message_text(f"❌ Не удалось завершить заявку \\#{request_id}\\.",
                                      parse_mode=ParseMode.MARKDOWN_V2)


async def show_comments(query, context, request_id):
    comments = await api_client.get_comments(request_id)
    if not comments:
        await query.answer("Комментариев пока нет.", show_alert=True)
        return

    text = f"💬 *Комментарии к заявке \\#{request_id}*\n\n"
    for comment in comments:
        created_at = datetime.datetime.fromisoformat(comment['createdAt']).strftime('%d.%m %H:%M')
        text += f"*{escape_markdown(comment['userLogin'])}* \\({escape_markdown(created_at)}\\):\n"
        text += f"{escape_markdown(comment['commentText'])}\n\n"

    keyboard = [[InlineKeyboardButton("◀️ Назад к заявке", callback_data=f"act_back_details_{request_id}")]]
    await query.edit_message_text(text, reply_markup=InlineKeyboardMarkup(keyboard), parse_mode=ParseMode.MARKDOWN_V2)


async def show_photos(query, context, request_id):
    photo_ids = await api_client.get_photo_ids(request_id)
    if not photo_ids:
        await query.answer("Фотографий нет.", show_alert=True)
        return

    await query.message.reply_text(f"Загружаю {len(photo_ids)} фото для заявки #{request_id}...")
    media_group = [InputMediaPhoto(media=await api_client.get_photo(pid)) for pid in
                   photo_ids[:10]]  # Ограничение Telegram на 10 фото в группе
    await query.message.reply_media_group(media=media_group)


async def add_comment_handler(update: Update, context: Context) -> int:
    """Обрабатывает ввод текста комментария и возвращает в VIEW_DETAILS."""
    comment_text = update.message.text
    request_id = context.user_data.get('current_request_id')
    user_id = update.effective_user.id

    await update.message.delete()

    response = await api_client.add_comment(request_id, user_id, comment_text)
    if not response:
        await context.bot.send_message(update.effective_chat.id, "❌ Не удалось добавить комментарий.")
    else:
        # Отправляем временное уведомление
        sent_message = await context.bot.send_message(update.effective_chat.id, "✅ Комментарий добавлен!")
        # Можно запланировать его удаление через несколько секунд

    # Возвращаемся к детальному просмотру
    class FakeUpdate:
        class FakeMessage:
            text = f"/{request_id}"

            async def reply_text(*args, **kwargs): pass

            async def delete(*args, **kwargs): pass

        message = FakeMessage()
        effective_user = update.effective_user

    return await view_request_details(FakeUpdate(), context)


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
    return CREATE_SELECT_SHOP


async def select_shop_callback(update: Update, context: CallbackContext) -> int | None:
    query = update.callback_query
    await query.answer()

    action, value = query.data.split('_', 2)[1:]

    if action == 'page':
        page = int(value)
        keyboard = create_paginated_keyboard(context.user_data['shops'], page, 'shop', 'shopName', 'shopID')
        await query.edit_message_text("<b>Шаг 1/5:</b> Выберите магазин:", reply_markup=keyboard,
                                      parse_mode=ParseMode.HTML)
        return CREATE_SELECT_SHOP

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
    return CREATE_SELECT_CONTRACTOR


async def select_contractor_callback(update: Update, context: CallbackContext) -> int | None:
    query = update.callback_query
    await query.answer()

    action, value = query.data.split('_', 2)[1:]

    if action == 'page':
        page = int(value)
        keyboard = create_paginated_keyboard(context.user_data['contractors'], page, 'contractor', 'login', 'userID')
        await query.edit_message_text("<b>Шаг 2/5:</b> Выберите подрядчика:", reply_markup=keyboard,
                                      parse_mode=ParseMode.HTML)
        return CREATE_SELECT_CONTRACTOR

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
    return CREATE_SELECT_WORK_CATEGORY


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
        return CREATE_SELECT_WORK_CATEGORY

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
    return CREATE_SELECT_URGENCY


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
        return CREATE_SELECT_URGENCY

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

        return CREATE_ENTER_DESCRIPTION
    return None


async def description_handler(update: Update, context: CallbackContext) -> int:
    description = update.message.text
    context.user_data['request_data']['description'] = description

    if context.user_data.get('is_customizable'):
        await update.message.reply_text(
            "Срочность 'Настраиваемая'. Введите количество дней на выполнение (например, 10)."
        )
        return CREATE_ENTER_CUSTOM_DAYS
    else:
        return await submit_request(update, context)


async def custom_days_handler(update: Update, context: CallbackContext) -> int:
    days = update.message.text
    if not days.isdigit() or not 1 <= int(days) <= 365:
        await update.message.reply_text("❌ Неверное значение. Введите число от 1 до 365.")
        return CREATE_ENTER_CUSTOM_DAYS

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