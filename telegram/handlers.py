from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup, InputMediaPhoto
import datetime
import math
import asyncio

from typing import Dict, Any, Coroutine, List, Tuple
from telegram import Update
from telegram.ext import ContextTypes, ConversationHandler, CallbackContext, ExtBot
from telegram.constants import ParseMode, ChatType
from telegram.error import BadRequest, TimedOut
import api_client
from utils import create_paginated_keyboard
from bot_logging import logger


class CustomContext(CallbackContext[ExtBot, Dict, Dict, Dict]):
    """Кастомный класс контекста для строгой типизации."""

    @classmethod
    def from_update(cls, update: object, application: object) -> "CustomContext":
        return cls(application=application, chat_id=update.effective_chat.id, user_id=update.effective_user.id)


Context = CustomContext

SORT_FIELDS: List[tuple[str, str]] = [
    ("requestID", "ID"),
    ("description", "Описание"),
    ("shopName", "Магазин"),
    ("workCategoryName", "Вид работы"),
    ("urgencyName", "Срочность"),
    ("assignedContractorName", "Исполнитель"),
    ("status", "Статус"),
    ("daysRemaining", "Срок"),
]
SORT_LABELS = dict(SORT_FIELDS)
SORT_EXTRACTORS = {
    "requestID": lambda r: r.get("requestID", 0),
    "description": lambda r: (r.get("description") or "").lower(),
    "shopName": lambda r: (r.get("shopName") or "").lower(),
    "workCategoryName": lambda r: (r.get("workCategoryName") or "").lower(),
    "urgencyName": lambda r: (r.get("urgencyName") or "").lower(),
    "assignedContractorName": lambda r: (r.get("assignedContractorName") or "").lower(),
    "status": lambda r: (r.get("status") or "").lower(),
    "daysRemaining": lambda r: (
        r.get("daysRemaining") if r.get("daysRemaining") is not None else float("inf")
    ),
}

BOT_PAGE_SIZE = 6
API_BATCH_SIZE = 50


async def safe_answer_query(query, **kwargs):
    try:
        await query.answer(**kwargs)
    except TimedOut:
        logger.warning("Timeout while answering callback '%s'", query.data)
    except Exception as exc:
        logger.error("Error answering callback '%s': %s", query.data, exc)


(CREATE_SELECT_SHOP, CREATE_SELECT_CONTRACTOR, CREATE_SELECT_WORK_CATEGORY,
 CREATE_SELECT_URGENCY, CREATE_ENTER_DESCRIPTION, CREATE_ENTER_CUSTOM_DAYS) = range(6)

(VIEW_MAIN_MENU, VIEW_SET_SEARCH_TERM, VIEW_SET_SORTING, VIEW_DETAILS,
 VIEW_COMMENT_LIST, VIEW_ADD_COMMENT, VIEW_PHOTO_LIST, VIEW_ADD_PHOTO) = range(6, 14)


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
        days_remaining_str = escape_markdown(str(req['daysRemaining']))
        deadline_info = f"{days_remaining_str} дн\\."
    else:
        deadline_info = "—"

    if req['isOverdue']:
        deadline_info = f"Просрочено\\! \\({deadline_info}\\)"

    executor = escape_markdown(req['assignedContractorName'] or 'Не назначен')
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


def _get_sort_list(filters: Dict[str, Any]) -> List[str]:
    sort_list = filters.get('sort')
    if not sort_list:
        sort_list = ['requestID,asc']
        filters['sort'] = sort_list
    return sort_list


def _apply_local_sort(requests: List[dict], sort_list: List[str]) -> List[dict]:
    if not requests:
        return requests
    parsed: List[Tuple] = []
    for entry in sort_list:
        parts = entry.split(",", 1)
        field = parts[0]
        direction = parts[1].lower() if len(parts) > 1 else "asc"
        extractor = SORT_EXTRACTORS.get(field)
        if extractor:
            parsed.append((extractor, direction == "desc"))
    for extractor, reverse in reversed(parsed):
        try:
            requests.sort(key=extractor, reverse=reverse)
        except Exception as exc:
            logger.warning("Local sort failed for field: %s (%s)", extractor, exc)
    return requests


def _build_cache_key(filters: Dict[str, Any]) -> Tuple:
    key_parts = []
    for key, value in filters.items():
        if key == 'page':
            continue
        if isinstance(value, list):
            key_parts.append((key, tuple(value)))
        else:
            key_parts.append((key, value))
    return tuple(sorted(key_parts))


async def _fetch_full_dataset(user_id: int, filters: Dict[str, Any]) -> List[dict] | None:
    base_filters = {k: v for k, v in filters.items() if k != 'page'}
    base_filters['size'] = API_BATCH_SIZE
    aggregated: List[dict] = []
    page = 0
    total_pages = 1
    while page < total_pages:
        base_filters['page'] = page
        response = await api_client.get_requests(user_id, base_filters)
        if response is None:
            return None
        aggregated.extend(response.get('content', []))
        total_pages = response.get('totalPages', page + 1)
        page += 1
        if page > 500:
            logger.warning("Aborting fetch: too many pages for filters %s", filters)
            break
    return aggregated


async def _get_sorted_dataset(user_id: int, context: Context) -> List[dict] | None:
    filters = context.user_data.get('view_filters', {})
    cache_key = _build_cache_key(filters)
    if context.user_data.get('requests_cache_key') == cache_key:
        cached = context.user_data.get('requests_cache')
        if cached is not None:
            return cached

    dataset = await _fetch_full_dataset(user_id, filters)
    if dataset is None:
        return None

    sort_list = _get_sort_list(filters)
    dataset = _apply_local_sort(dataset, sort_list)
    context.user_data['requests_cache_key'] = cache_key
    context.user_data['requests_cache'] = dataset
    return dataset


def _slice_page(requests: List[dict], page: int) -> tuple[List[dict], int]:
    if not requests:
        return [], 0
    total_pages = math.ceil(len(requests) / BOT_PAGE_SIZE)
    page = min(max(page, 0), total_pages - 1)
    start = page * BOT_PAGE_SIZE
    end = start + BOT_PAGE_SIZE
    return requests[start:end], total_pages


def _invalidate_requests_cache(context: Context):
    context.user_data.pop('requests_cache', None)
    context.user_data.pop('requests_cache_key', None)


def _format_sort_list(sort_list: List[str]) -> str:
    if not sort_list:
        sort_list = ['requestID,asc']
    lines = []
    for idx, sort_param in enumerate(sort_list, start=1):
        field, *direction = sort_param.split(',', 1)
        dir_value = (direction[0] if direction else 'asc').lower()
        arrow = "⬆️" if dir_value == 'asc' else "⬇️"
        label = escape_markdown(SORT_LABELS.get(field, field))
        lines.append(f"{idx}\\.\u00A0{label} {arrow}")
    return "\n".join(lines)


def _build_sort_overview(filters: Dict[str, Any]) -> str:
    sort_list = _get_sort_list(filters)
    overview = _format_sort_list(sort_list)
    return f"{escape_markdown('Текущая сортировка:')}\n{overview}\n\n{escape_markdown('Выберите поле, чтобы настроить порядок.')}"


def _get_sort_field_keyboard(filters: Dict[str, Any]) -> InlineKeyboardMarkup:
    sort_list = _get_sort_list(filters)
    buttons = []
    for field, label in SORT_FIELDS:
        active_index = next((i for i, s in enumerate(sort_list) if s.startswith(field + ",")), None)
        if active_index is not None:
            direction = sort_list[active_index].split(',')[1]
            arrow = "⬆️" if direction == 'asc' else "⬇️"
            suffix = f" {arrow} ({active_index + 1})"
        else:
            suffix = ""
        buttons.append([InlineKeyboardButton(f"{label}{suffix}", callback_data=f"sort_field_{field}")])

    buttons.append([
        InlineKeyboardButton("🧹 Очистить", callback_data="sort_clear"),
        InlineKeyboardButton("✅ Готово", callback_data="sort_done")
    ])
    buttons.append([InlineKeyboardButton("◀️ Назад", callback_data="view_back_main")])
    return InlineKeyboardMarkup(buttons)


def _get_sort_direction_keyboard(field: str) -> InlineKeyboardMarkup:
    label = SORT_LABELS.get(field, field)
    return InlineKeyboardMarkup([
        [InlineKeyboardButton(f"⬆️ {label} (возр.)", callback_data=f"sort_set_{field}_asc")],
        [InlineKeyboardButton(f"⬇️ {label} (убыв.)", callback_data=f"sort_set_{field}_desc")],
        [InlineKeyboardButton("🗑 Удалить из сортировки", callback_data=f"sort_remove_{field}")],
        [InlineKeyboardButton("◀️ Назад", callback_data="sort_back")]
    ])


async def view_requests_start(update: Update, context: Context) -> int:
    user_id = update.effective_user.id
    user_info = await api_client.get_user_by_telegram_id(user_id)
    if not user_info:
        await update.message.reply_text("❌ Ваш Telegram ID не найден в системе.")
        return ConversationHandler.END

    context.user_data['view_filters'] = {'archived': False, 'page': 0, 'sort': ['requestID,asc']}
    _invalidate_requests_cache(context)
    context.user_data['user_info'] = user_info

    placeholder_message = await update.message.reply_text("🔄 Загружаю список заявок...")
    context.user_data['main_message_id'] = placeholder_message.message_id

    return await render_main_view_menu(update, context)


async def render_main_view_menu(update: Update, context: Context, is_callback: bool = False) -> int:
    user_id = update.effective_user.id
    filters = context.user_data.get('view_filters', {})
    logger.debug("Bot filters for requests: %s", filters)
    dataset = await _get_sorted_dataset(user_id, context)
    if dataset is None:
        error_text = "❌ Не удалось загрузить список заявок. Попробуйте позже."
        if is_callback:
            await update.callback_query.edit_message_text(error_text)
        else:
            await context.bot.send_message(update.effective_chat.id, error_text)
        return VIEW_MAIN_MENU

    page = filters.get('page', 0)
    requests, total_pages = _slice_page(dataset, page)
    if total_pages:
        filters['page'] = min(max(page, 0), total_pages - 1)
    else:
        filters['page'] = 0
    filter_lines = []
    if filters.get('archived'): filter_lines.append("Тип: Архив")
    if filters.get('searchTerm'): filter_lines.append(f"Поиск: '{escape_markdown(filters['searchTerm'])}'")
    sort_list = _get_sort_list(filters)
    filter_lines.append(f"{escape_markdown('Сортировка:')}\n{_format_sort_list(sort_list)}")

    filter_text = "\n".join(filter_lines)
    message_text = f"⚙️ *Активные фильтры:*\n{filter_text}\n\n"
    if not requests:
        message_text += "_Заявок по вашим фильтрам не найдено\\._"
    else:
        message_text += "\n\n".join(format_request_list_item(req) for req in requests)
        message_text += "\n\n" + escape_markdown("Нажмите ℹ️ рядом с номером, чтобы открыть заявку.")

    keyboard = []
    if requests:
        row = []
        for idx, req in enumerate(requests, start=1):
            row.append(InlineKeyboardButton(f"ℹ️ #{req['requestID']}", callback_data=f"view_req_{req['requestID']}"))
            if idx % 3 == 0:
                keyboard.append(row)
                row = []
        if row:
            keyboard.append(row)

    nav_row = []
    current_page = filters.get('page', 0)
    if total_pages:
        if current_page > 0:
            nav_row.append(InlineKeyboardButton("⬅️", callback_data="view_page_prev"))
        if total_pages > 1:
            nav_row.append(InlineKeyboardButton(f"{current_page + 1}/{total_pages}", callback_data="noop"))
        if current_page < total_pages - 1:
            nav_row.append(InlineKeyboardButton("➡️", callback_data="view_page_next"))

    keyboard.append([
        InlineKeyboardButton("🔎 Поиск", callback_data="view_search"),
        InlineKeyboardButton("📊 Сортировка", callback_data="view_sort"),
    ])
    keyboard.append([
        InlineKeyboardButton("🗂 Архив" if not filters.get('archived') else "📂 Активные",
                             callback_data="view_toggle_archive"),
        InlineKeyboardButton("🔄 Сброс", callback_data="view_reset")
    ])
    if nav_row:
        keyboard.append(nav_row)
    keyboard.append([InlineKeyboardButton("❌ Закрыть", callback_data="view_exit")])

    reply_markup = InlineKeyboardMarkup(keyboard)
    try:
        message_id = None
        if hasattr(update, 'callback_query') and update.callback_query and update.callback_query.message:
            message_id = update.callback_query.message.message_id
        elif context.user_data.get('main_message_id'):
            message_id = context.user_data['main_message_id']

        if message_id:
            await context.bot.edit_message_text(
                text=message_text,
                chat_id=update.effective_chat.id,
                message_id=message_id,
                reply_markup=reply_markup,
                parse_mode=ParseMode.MARKDOWN_V2
            )
            context.user_data['main_message_id'] = message_id
        else:
            sent_message = await context.bot.send_message(
                chat_id=update.effective_chat.id,
                text=message_text,
                reply_markup=reply_markup,
                parse_mode=ParseMode.MARKDOWN_V2
            )
            context.user_data['main_message_id'] = sent_message.message_id

    except Exception as e:
        logger.error(f"Ошибка отправки сообщения Markdown: {e}\nТекст: {message_text}")
        await context.bot.send_message(
            chat_id=update.effective_chat.id,
            text="Произошла ошибка форматирования или отображения."
        )
    return VIEW_MAIN_MENU


async def view_menu_callback(update: Update, context: Context) -> int:
    query = update.callback_query
    await safe_answer_query(query)
    data = query.data

    if data.startswith('view_req_'):
        request_id = int(data.split('_', 2)[2])
        return await show_request_details_in_message(query, context, request_id)

    action = data.split('_', 1)[1]
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
        _invalidate_requests_cache(context)
    elif action == 'reset':
        context.user_data['view_filters'] = {'archived': False, 'page': 0, 'sort': ['requestID,asc']}
        _invalidate_requests_cache(context)
    elif action == 'search':
        await query.edit_message_text("Введите текст для поиска по описанию заявки:")
        return VIEW_SET_SEARCH_TERM
    elif action == 'sort':
        await _show_sort_menu(query, context)
        return VIEW_SET_SORTING

    await render_main_view_menu(update, context, is_callback=True)
    return VIEW_MAIN_MENU


async def show_request_details_in_message(query, context: Context, request_id: int) -> int:
    """Отображает детали заявки, редактируя сообщение со списком."""
    user_id = query.from_user.id
    user_info = context.user_data.get('user_info') or await api_client.get_user_by_telegram_id(user_id)
    if not user_info:
        await query.answer("❌ Ваш Telegram ID не найден в системе.", show_alert=True)
        return VIEW_MAIN_MENU

    request_details = await api_client.get_request_details(user_id, request_id)
    if not request_details:
        await query.answer(f"❌ Не удалось найти заявку #{request_id}", show_alert=True)
        return VIEW_MAIN_MENU

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
        second_action_row.append(InlineKeyboardButton("📷 Добавить фото", callback_data=f"act_add_photo_{request_id}"))
    if role == 'RetailAdmin':
        second_action_row.append(InlineKeyboardButton("✏️ Изменить", callback_data=f"act_edit_{request_id}"))
    if role == 'Contractor' and status == 'In work':
        second_action_row.append(InlineKeyboardButton("✅ Завершить", callback_data=f"act_complete_{request_id}"))
    if second_action_row: keyboard.append(second_action_row)

    keyboard.append([InlineKeyboardButton("◀️ Назад к списку", callback_data="act_back_list")])

    try:
        if query.message:
            context.user_data['main_message_id'] = query.message.message_id

        await query.edit_message_text(
            text=message_text,
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode=ParseMode.MARKDOWN_V2
        )
    except Exception as e:
        logger.error(f"Ошибка редактирования сообщения: {e}")
        await query.answer("Ошибка отображения заявки", show_alert=True)

    return VIEW_DETAILS


async def _show_sort_menu(query, context: Context):
    filters = context.user_data.get('view_filters', {})
    text = _build_sort_overview(filters)
    await _edit_message_markdown(query, text, _get_sort_field_keyboard(filters))


async def _edit_message_markdown(query, text, reply_markup=None):
    try:
        await query.edit_message_text(
            text=text,
            reply_markup=reply_markup,
            parse_mode=ParseMode.MARKDOWN_V2
        )
    except BadRequest as e:
        if "Message is not modified" in str(e):
            await safe_answer_query(query, text="Без изменений", show_alert=False)
        else:
            logger.error(f"Ошибка отображения сортировки: {e} | текст: {text}")
            await safe_answer_query(query, text="Ошибка отображения. Попробуйте ещё раз.", show_alert=True)


async def view_sort_callback(update: Update, context: Context) -> int:
    query = update.callback_query
    await safe_answer_query(query)
    data = query.data
    filters = context.user_data.get('view_filters', {})

    if data == "view_back_main":
        await render_main_view_menu(update, context, is_callback=True)
        return VIEW_MAIN_MENU

    if data == "sort_done":
        filters['page'] = 0
        await render_main_view_menu(update, context, is_callback=True)
        return VIEW_MAIN_MENU

    if data == "sort_clear":
        filters['sort'] = ['requestID,asc']
        filters['page'] = 0
        _invalidate_requests_cache(context)
        await _show_sort_menu(query, context)
        return VIEW_SET_SORTING

    if data == "sort_back":
        await _show_sort_menu(query, context)
        return VIEW_SET_SORTING

    if data.startswith("sort_field_"):
        field = data.split("_", 2)[2]
        label = escape_markdown(SORT_LABELS.get(field, field))
        text = f"*Поле:* {label}\n{escape_markdown('Выберите направление сортировки:')}"
        await _edit_message_markdown(query, text, _get_sort_direction_keyboard(field))
        return VIEW_SET_SORTING

    if data.startswith("sort_set_"):
        _, _, field, direction = data.split("_", 3)
        sort_list = [s for s in _get_sort_list(filters) if not s.startswith(field + ",")]
        sort_list.append(f"{field},{direction}")
        filters['sort'] = sort_list
        filters['page'] = 0
        _invalidate_requests_cache(context)
        await render_main_view_menu(update, context, is_callback=True)
        return VIEW_MAIN_MENU

    if data.startswith("sort_remove_"):
        field = data.split("_", 2)[2]
        sort_list = [s for s in _get_sort_list(filters) if not s.startswith(field + ",")]
        filters['sort'] = sort_list if sort_list else ['requestID,asc']
        filters['page'] = 0
        _invalidate_requests_cache(context)
        await _show_sort_menu(query, context)
        return VIEW_SET_SORTING

    return VIEW_SET_SORTING


async def view_search_handler(update: Update, context: Context) -> int:
    filters = context.user_data.get('view_filters', {})
    filters['searchTerm'] = update.message.text
    filters['page'] = 0
    _invalidate_requests_cache(context)
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
        second_action_row.append(InlineKeyboardButton("📷 Добавить фото", callback_data=f"act_add_photo_{request_id}"))
    if role == 'Contractor' and status == 'In work':
        second_action_row.append(InlineKeyboardButton("✅ Завершить", callback_data=f"act_complete_{request_id}"))
    if second_action_row: keyboard.append(second_action_row)

    keyboard.append([InlineKeyboardButton("◀️ Назад к списку", callback_data="act_back_list")])

    await update.message.reply_text(message_text, reply_markup=InlineKeyboardMarkup(keyboard),
                                    parse_mode=ParseMode.MARKDOWN_V2)
    return VIEW_DETAILS


async def action_callback_handler(update: Update, context: Context) -> int | None:
    query = update.callback_query
    await safe_answer_query(query)

    parts = query.data.split('_')
    action = "_".join(parts[1:-1]) if len(parts) > 2 else parts[1]
    value = parts[-1] if len(parts) > 1 else None

    if action == 'back' and value == 'list':
        class FakeUpdate:
            def __init__(self, query):
                class FakeCallbackQuery:
                    def __init__(self, q):
                        self.from_user = q.from_user
                        self.data = q.data
                        self.message = q.message

                self.callback_query = FakeCallbackQuery(query)
                self.effective_chat = query.message.chat
                self.effective_user = query.from_user

        fake_update = FakeUpdate(query)
        return await render_main_view_menu(fake_update, context, is_callback=True)

    elif action == 'back' and value == 'details':
        await query.delete_message()

        class FakeUpdate:
            class FakeMessage:
                text = f"/{value}"

            message = FakeMessage()
            effective_user = query.from_user

        return await view_request_details(FakeUpdate(), context)

    elif action == 'back_to_request':
        request_id = int(value)
        return await show_request_details_in_message(query, context, request_id)

    elif action == 'complete':
        await complete_request_action(query, context, int(value))
        return VIEW_DETAILS

    elif action == 'comments':
        await show_comments(query, context, int(value))
        return VIEW_DETAILS

    elif action == 'photos':
        await show_photos(query, context, int(value))
        return VIEW_DETAILS

    elif action == 'add_comment':
        await query.edit_message_text("Введите текст вашего комментария:")
        context.user_data['current_request_id'] = int(value)
        if query.message:
            context.user_data['comment_input_message_id'] = query.message.message_id
        return VIEW_ADD_COMMENT

    elif action == 'add_photo':
        # УДАЛЕНИЕ МЕНЮ ЗАЯВКИ ПРИ ПЕРЕХОДЕ К ЗАГРУЗКЕ ФОТО
        await query.delete_message()

        request_id = int(value)
        context.user_data['current_request_id'] = request_id

        # Отправляем новое сообщение-приглашение с упоминанием лимита
        prompt_message = await context.bot.send_message(
            chat_id=update.effective_chat.id,
            text="📤 Пожалуйста, отправьте фото для заявки. Максимум 10 фото на заявку."
        )
        # Сохраняем ID сообщения-приглашения, чтобы потом удалить
        context.user_data['photo_prompt_message_id'] = prompt_message.message_id

        return VIEW_ADD_PHOTO

    elif action == 'edit':
        # Запускаем диалог редактирования
        # Нам нужно выйти из текущего ConversationHandler просмотра и зайти в ConversationHandler редактора
        # Но Telegram bot lib не позволяет легко перепрыгивать между независимыми ConversationHandler.
        # Поэтому мы добавим состояния редактора ВНУТРЬ общего view_conv (см. ниже в main.py)
        return await start_edit_request(update, context)
    return None


async def complete_request_action(query, context, request_id):
    await query.edit_message_text(f"Завершаю заявку \\#{request_id}\\.\\.\\.", parse_mode=ParseMode.MARKDOWN_V2)
    response = await api_client.complete_request(query.from_user.id, request_id)
    if response:
        _invalidate_requests_cache(context)
        await query.edit_message_text(f"✅ Заявка \\#{request_id} успешно завершена\\.",
                                      parse_mode=ParseMode.MARKDOWN_V2)
    else:
        await query.edit_message_text(f"❌ Не удалось завершить заявку \\#{request_id}\\.",
                                      parse_mode=ParseMode.MARKDOWN_V2)


async def show_comments(query, context, request_id):
    comments = await api_client.get_comments(request_id)
    if not comments:
        await safe_answer_query(query, text="Комментариев пока нет.", show_alert=True)
        return

    text = f"💬 *Комментарии к заявке \\#{request_id}*\n\n"
    for comment in comments:
        created_at = datetime.datetime.fromisoformat(comment['createdAt']).strftime('%d.%m %H:%M')
        text += f"*{escape_markdown(comment['userLogin'])}* \\({escape_markdown(created_at)}\\):\n"
        text += f"{escape_markdown(comment['commentText'])}\n\n"

    keyboard = [[InlineKeyboardButton("◀️ Назад к заявке", callback_data=f"act_back_to_request_{request_id}")]]
    await query.edit_message_text(text, reply_markup=InlineKeyboardMarkup(keyboard), parse_mode=ParseMode.MARKDOWN_V2)


async def show_photos(query, context, request_id):
    photo_ids = await api_client.get_photo_ids(request_id)
    if not photo_ids:
        await safe_answer_query(query, text="Фотографий нет.", show_alert=True)
        return

    status_message = await query.message.reply_text(f"Загружаю {len(photo_ids)} фото для заявки #{request_id}...")

    media_group = []
    for pid in photo_ids[:10]:
        photo_bytes = await api_client.get_photo(pid)
        if photo_bytes:
            media_group.append(InputMediaPhoto(media=photo_bytes))

    media_messages = []
    if media_group:
        media_messages = await query.message.reply_media_group(media=media_group)
    else:
        await query.message.reply_text("❌ Не удалось загрузить изображения.")

    # Асинхронное удаление сообщений через 20 секунд
    async def delete_viewed_photos():
        try:
            await asyncio.sleep(20)
            try:
                await context.bot.delete_message(chat_id=query.message.chat_id, message_id=status_message.message_id)
            except Exception as e:
                logger.warning(f"Failed to delete status message: {e}")

            for msg in media_messages:
                try:
                    await context.bot.delete_message(chat_id=query.message.chat_id, message_id=msg.message_id)
                except Exception as e:
                    logger.warning(f"Failed to delete photo message: {e}")
        except Exception as e:
            logger.error(f"Error in delayed photo deletion: {e}")

    asyncio.create_task(delete_viewed_photos())


async def add_comment_handler(update: Update, context: Context) -> int:
    comment_text = update.message.text
    request_id = context.user_data.get('current_request_id')
    user_id = update.effective_user.id

    await update.message.delete()

    comment_input_msg_id = context.user_data.get('comment_input_message_id')
    if comment_input_msg_id:
        try:
            await context.bot.delete_message(chat_id=update.effective_chat.id, message_id=comment_input_msg_id)
        except Exception as e:
            logger.warning(f"Не удалось удалить сообщение ввода комментария: {e}")
        context.user_data.pop('comment_input_message_id', None)

    response = await api_client.add_comment(request_id, user_id, comment_text)
    if not response:
        await context.bot.send_message(update.effective_chat.id, "❌ Не удалось добавить комментарий.")
    else:
        escaped_comment = escape_markdown(comment_text[:100])
        if len(comment_text) > 100:
            escaped_comment += "..."
        message_text = (
            f"✅ *Комментарий добавлен к заявке \\#{request_id}*\n\n"
            f"*Комментарий:*\n{escaped_comment}"
        )
        sent_message = await context.bot.send_message(
            update.effective_chat.id,
            message_text,
            parse_mode=ParseMode.MARKDOWN_V2
        )

        async def delete_notification():
            try:
                await asyncio.sleep(10)
                await context.bot.delete_message(
                    chat_id=update.effective_chat.id,
                    message_id=sent_message.message_id
                )
            except Exception as e:
                logger.warning(f"Не удалось удалить уведомление: {e}")

        asyncio.create_task(delete_notification())

    _invalidate_requests_cache(context)

    user_info = context.user_data.get('user_info') or await api_client.get_user_by_telegram_id(user_id)
    if not user_info:
        return VIEW_MAIN_MENU

    request_details = await api_client.get_request_details(user_id, request_id)
    if not request_details:
        return VIEW_MAIN_MENU

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
        action_row.append(InlineKeyboardButton(f"🖼️ Просмотр фото ({request_details['photoCount']})",
                                               callback_data=f"act_photos_{request_id}"))
    if action_row:
        keyboard.append(action_row)

    second_action_row = []
    if role in ['RetailAdmin', 'Contractor'] and status != 'Closed':
        second_action_row.append(InlineKeyboardButton("➕ Комментарий", callback_data=f"act_add_comment_{request_id}"))
        second_action_row.append(InlineKeyboardButton("📷 Добавить фото", callback_data=f"act_add_photo_{request_id}"))
    if role == 'Contractor' and status == 'In work':
        second_action_row.append(InlineKeyboardButton("✅ Завершить", callback_data=f"act_complete_{request_id}"))
    if second_action_row:
        keyboard.append(second_action_row)

    keyboard.append([InlineKeyboardButton("◀️ Назад к списку", callback_data="act_back_list")])

    main_message_id = context.user_data.get('main_message_id')
    if main_message_id:
        try:
            await context.bot.edit_message_text(
                text=message_text,
                chat_id=update.effective_chat.id,
                message_id=main_message_id,
                reply_markup=InlineKeyboardMarkup(keyboard),
                parse_mode=ParseMode.MARKDOWN_V2
            )
        except Exception as e:
            logger.error(f"Ошибка редактирования сообщения: {e}")

    return VIEW_DETAILS


async def add_photo_handler(update: Update, context: Context) -> int:
    """Обрабатывает загрузку фото (сжатого или файла) для заявки с группировкой."""
    request_id = context.user_data.get('current_request_id')
    user_id = update.effective_user.id

    if not request_id:
        await update.message.reply_text("❌ Ошибка: не найдена заявка.")
        return VIEW_MAIN_MENU

    # 1. Получение фото
    photo_bytes = None
    if update.message.photo:
        photo = update.message.photo[-1]
        photo_file = await context.bot.get_file(photo.file_id)
        photo_bytes = await photo_file.download_as_bytearray()
    elif update.message.document and update.message.document.mime_type.startswith('image/'):
        photo_file = await context.bot.get_file(update.message.document.file_id)
        photo_bytes = await photo_file.download_as_bytearray()

    if not photo_bytes:
        await update.message.reply_text("❌ Не удалось получить фото. Пожалуйста, отправьте изображение.")
        return VIEW_ADD_PHOTO

    # Удаляем сообщение пользователя с фото (для чистоты чата)
    try:
        await update.message.delete()
    except Exception:
        pass

    # 2. Удаление приглашения (только один раз)
    photo_prompt_id = context.user_data.get('photo_prompt_message_id')
    if photo_prompt_id:
        try:
            await context.bot.delete_message(chat_id=update.effective_chat.id, message_id=photo_prompt_id)
        except Exception:
            pass
        context.user_data.pop('photo_prompt_message_id', None)

    # 3. Логика группировки (MediaGroup)
    media_group_id = update.message.media_group_id

    if not media_group_id:
        # Одиночное фото - обрабатываем сразу как список из одного элемента
        return await finalize_photo_upload(context, update.effective_chat.id, user_id, request_id, [photo_bytes])

    # Работа с группой
    if 'upload_buffer' not in context.user_data:
        context.user_data['upload_buffer'] = {}

    if media_group_id not in context.user_data['upload_buffer']:
        context.user_data['upload_buffer'][media_group_id] = []
        # Запускаем таймер на 2 секунды
        asyncio.create_task(process_media_group(context, media_group_id, update.effective_chat.id, user_id, request_id))

    context.user_data['upload_buffer'][media_group_id].append(photo_bytes)

    # Остаемся в том же состоянии, чтобы поймать следующие фото
    return VIEW_ADD_PHOTO


async def process_media_group(context, media_group_id, chat_id, user_id, request_id):
    """Фоновая задача для обработки группы фото."""
    await asyncio.sleep(2)  # Ждем, пока дойдут все фото

    buffer = context.user_data.get('upload_buffer', {}).pop(media_group_id, [])
    if not buffer:
        return

    await finalize_photo_upload(context, chat_id, user_id, request_id, buffer)


async def finalize_photo_upload(context, chat_id, user_id, request_id, photos):
    """Отправка фото на сервер и восстановление меню."""
    # Проверка лимита (запрашиваем данные с сервера)
    req_details = await api_client.get_request_details(user_id, request_id)

    if req_details:
        current_count = req_details.get('photoCount', 0)
        incoming_count = len(photos)

        if current_count + incoming_count > 10:
            error_msg = await context.bot.send_message(
                chat_id=chat_id,
                text=f"❌ Ошибка: Лимит 10 фото. Уже загружено: {current_count}. Пытались добавить: {incoming_count}."
            )

            async def delete_error():
                await asyncio.sleep(5)
                try:
                    await context.bot.delete_message(chat_id=chat_id, message_id=error_msg.message_id)
                except:
                    pass

            asyncio.create_task(delete_error())

            # Восстанавливаем меню даже при ошибке
            await restore_request_menu(context, chat_id, user_id, request_id)
            return VIEW_DETAILS

    # Загрузка
    success = await api_client.upload_photos(request_id, user_id, photos)

    if success:
        _invalidate_requests_cache(context)
        message_text = f"✅ *Добавлено фото: {len(photos)} шт\. к заявке \\#{request_id}*"
        sent_message = await context.bot.send_message(
            chat_id=chat_id,
            text=message_text,  # Исправлено message_text=message_text -> text=message_text
            parse_mode=ParseMode.MARKDOWN_V2
        )

        async def delete_notification():
            try:
                await asyncio.sleep(10)
                await context.bot.delete_message(chat_id=chat_id, message_id=sent_message.message_id)
            except Exception:
                pass

        asyncio.create_task(delete_notification())
    else:
        await context.bot.send_message(chat_id=chat_id, text=f"❌ Не удалось загрузить фото для заявки #{request_id}.")

    await restore_request_menu(context, chat_id, user_id, request_id)
    return VIEW_DETAILS


async def restore_request_menu(context, chat_id, user_id, request_id):
    """Восстанавливает сообщение с деталями заявки."""
    user_info = context.user_data.get('user_info') or await api_client.get_user_by_telegram_id(user_id)
    req_details = await api_client.get_request_details(user_id, request_id)

    if not user_info or not req_details:
        return

    context.user_data['current_request_details'] = req_details
    message_text = format_request_details(req_details)

    role, status = user_info.get('roleName'), req_details.get('status')
    keyboard = []
    action_row = []
    if req_details.get('commentCount', 0) > 0:
        action_row.append(InlineKeyboardButton(f"💬 Комментарии ({req_details['commentCount']})",
                                               callback_data=f"act_comments_{request_id}"))
    if req_details.get('photoCount', 0) > 0:
        action_row.append(InlineKeyboardButton(f"🖼️ Просмотр фото ({req_details['photoCount']})",
                                               callback_data=f"act_photos_{request_id}"))
    if action_row: keyboard.append(action_row)

    second_action_row = []
    if role in ['RetailAdmin', 'Contractor'] and status != 'Closed':
        second_action_row.append(InlineKeyboardButton("➕ Комментарий", callback_data=f"act_add_comment_{request_id}"))
        second_action_row.append(InlineKeyboardButton("📷 Добавить фото", callback_data=f"act_add_photo_{request_id}"))
    if role == 'Contractor' and status == 'In work':
        second_action_row.append(InlineKeyboardButton("✅ Завершить", callback_data=f"act_complete_{request_id}"))
    if second_action_row: keyboard.append(second_action_row)

    keyboard.append([InlineKeyboardButton("◀️ Назад к списку", callback_data="act_back_list")])

    sent_menu = await context.bot.send_message(
        chat_id=chat_id,
        text=message_text,
        reply_markup=InlineKeyboardMarkup(keyboard),
        parse_mode=ParseMode.MARKDOWN_V2
    )
    context.user_data['main_message_id'] = sent_menu.message_id


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
    await safe_answer_query(query)

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
    await safe_answer_query(query)

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
    await safe_answer_query(query)

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
    await safe_answer_query(query)

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


(
    EDITOR_MAIN_MENU,       # Главное меню редактора (показывает текущие поля)
    EDITOR_SELECT_SHOP,     # Выбор магазина
    EDITOR_SELECT_CONTRACTOR, # Выбор подрядчика
    EDITOR_SELECT_WORK,     # Выбор вида работ
    EDITOR_SELECT_URGENCY,  # Выбор срочности
    EDITOR_INPUT_TEXT,      # Ввод описания или кастомных дней
    EDITOR_SELECT_STATUS    # Выбор статуса (только для редактирования)
) = range(20, 27)


# handlers.py - Добавить новые функции

def _get_editor_keyboard(draft: dict, is_new: bool, role: str) -> InlineKeyboardMarkup:
    """Генерирует клавиатуру для редактора заявки."""
    buttons = []

    # Иконки состояния полей
    shop_ico = "✅" if draft.get('shopID') else "❌"
    contr_ico = "✅" if draft.get('assignedContractorID') else "❌"
    work_ico = "✅" if draft.get('workCategoryID') else "❌"
    urg_ico = "✅" if draft.get('urgencyID') else "❌"
    desc_ico = "✅" if draft.get('description') else "❌"

    # 1. Основные поля
    buttons.append([InlineKeyboardButton(f"{shop_ico} Магазин", callback_data="edit_field_shop")])
    buttons.append([InlineKeyboardButton(f"{contr_ico} Исполнитель", callback_data="edit_field_contractor")])
    buttons.append([InlineKeyboardButton(f"{work_ico} Вид работ", callback_data="edit_field_work")])
    buttons.append([InlineKeyboardButton(f"{urg_ico} Срочность", callback_data="edit_field_urgency")])
    buttons.append([InlineKeyboardButton(f"{desc_ico} Описание", callback_data="edit_field_desc")])

    # 2. Статус (только при редактировании существующей)
    if not is_new:
        status_label = draft.get('status', 'In work')
        buttons.append([InlineKeyboardButton(f"Статус: {status_label}", callback_data="edit_field_status")])

    # 3. Кнопка сохранения
    # Разрешаем сохранять только если все обязательные поля заполнены
    is_ready = all([
        draft.get('shopID'),
        draft.get('assignedContractorID'),
        draft.get('workCategoryID'),
        draft.get('urgencyID'),
        draft.get('description')
    ])

    save_text = "💾 Создать заявку" if is_new else "💾 Сохранить изменения"
    if is_ready:
        buttons.append([InlineKeyboardButton(save_text, callback_data="editor_save")])

    buttons.append([InlineKeyboardButton("🔙 Отмена / Выход", callback_data="editor_cancel")])

    return InlineKeyboardMarkup(buttons)


async def render_editor_menu(update: Update, context: Context):
    """Отрисовывает меню редактора (черновика)."""
    draft = context.user_data.get('editor_draft', {})
    is_new = context.user_data.get('editor_is_new', True)
    user_info = context.user_data.get('user_info', {})

    # Формируем текст предпросмотра
    text = f"🛠 <b>{'СОЗДАНИЕ' if is_new else 'РЕДАКТИРОВАНИЕ'} ЗАЯВКИ</b>\n\n"

    # Получаем названия из ID (если они есть в кэше, иначе просто ID)
    shop_name = draft.get('shopName', '--- Не выбрано ---')
    contr_name = draft.get('contractorName', '--- Не выбрано ---')
    work_name = draft.get('workCategoryName', '--- Не выбрано ---')
    urg_name = draft.get('urgencyName', '--- Не выбрано ---')

    if draft.get('customDays'):
        urg_name += f" ({draft['customDays']} дн.)"

    desc = draft.get('description', '--- Не заполнено ---')

    text += f"🏪 <b>Магазин:</b> {shop_name}\n"
    text += f"👷 <b>Исполнитель:</b> {contr_name}\n"
    text += f"📋 <b>Вид работ:</b> {work_name}\n"
    text += f"🔥 <b>Срочность:</b> {urg_name}\n"
    text += f"📝 <b>Описание:</b>\n<i>{escape_markdown(desc[:100])}{'...' if len(desc) > 100 else ''}</i>\n"

    if not is_new:
        text += f"\n📊 <b>Статус:</b> {draft.get('status', 'In work')}"

    keyboard = _get_editor_keyboard(draft, is_new, user_info.get('roleName'))

    if update.callback_query:
        # Пытаемся отредактировать сообщение
        try:
            await update.callback_query.edit_message_text(text, reply_markup=keyboard, parse_mode=ParseMode.HTML)
        except BadRequest:
            # Если текст не изменился (например, нажали назад), ничего страшного
            pass
    else:
        await update.message.reply_text(text, reply_markup=keyboard, parse_mode=ParseMode.HTML)

    return EDITOR_MAIN_MENU


# --- Точки входа ---

async def start_create_request(update: Update, context: Context) -> int:
    """Начинает процесс создания новой заявки."""
    user_id = update.effective_user.id
    user_data = await api_client.get_user_by_telegram_id(user_id)

    if not user_data or user_data.get("roleName") != "RetailAdmin":
        await update.message.reply_text("❌ Только администратор может создавать заявки.")
        return ConversationHandler.END

    context.user_data['user_info'] = user_data
    context.user_data['editor_is_new'] = True
    context.user_data['editor_draft'] = {
        'createdByUserID': user_data['userID'],
        'status': 'In work'
    }

    # Предзагрузка словарей для быстрого отображения имен
    await _preload_dictionaries(context)

    return await render_editor_menu(update, context)


async def start_edit_request(update: Update, context: Context) -> int:
    """Начинает процесс редактирования существующей заявки."""
    query = update.callback_query
    request_id = int(query.data.split('_')[-1])
    user_id = update.effective_user.id

    # Получаем полные данные заявки
    req = await api_client.get_request_details(user_id, request_id)
    if not req:
        await query.answer("Ошибка загрузки заявки", show_alert=True)
        return VIEW_DETAILS

    user_data = await api_client.get_user_by_telegram_id(user_id)
    context.user_data['user_info'] = user_data
    context.user_data['editor_is_new'] = False

    # Заполняем черновик текущими данными
    context.user_data['editor_draft'] = {
        'requestID': req['requestID'],
        'description': req['description'],
        'shopID': req['shopID'],
        'shopName': req['shopName'],  # Сохраняем имя для отображения
        'workCategoryID': req['workCategoryID'],
        'workCategoryName': req['workCategoryName'],
        'urgencyID': req['urgencyID'],
        'urgencyName': req['urgencyName'],
        'assignedContractorID': req['assignedContractorID'],
        'contractorName': req['assignedContractorName'],
        'status': req['status'],
        'daysForTask': req['daysForTask']
    }

    # Если срочность настраиваемая, сохраняем customDays
    if req['urgencyName'] == 'Customizable':
        context.user_data['editor_draft']['customDays'] = req['daysForTask']

    await _preload_dictionaries(context)
    return await render_editor_menu(update, context)


async def _preload_dictionaries(context: Context):
    """Загружает списки один раз, чтобы отображать имена в меню."""
    # В реальном приложении лучше кэшировать это глобально или использовать Redis
    shops = await api_client.get_all_shops()
    contractors = await api_client.get_all_contractors()
    works = await api_client.get_all_work_categories()
    urgencies = await api_client.get_all_urgency_categories()

    context.user_data['dict_shops'] = shops.get('content', []) if shops else []
    context.user_data['dict_contractors'] = contractors if contractors else []
    context.user_data['dict_works'] = works.get('content', []) if works else []
    context.user_data['dict_urgencies'] = urgencies if urgencies else []


# handlers.py - продолжение

async def editor_main_callback(update: Update, context: Context) -> int:
    """Обработчик нажатий в главном меню редактора."""
    query = update.callback_query
    await safe_answer_query(query)
    data = query.data

    if data == "editor_cancel":
        await query.delete_message()
        return ConversationHandler.END

    elif data == "editor_save":
        return await _submit_editor_data(update, context)

    # Переходы к выбору полей
    elif data == "edit_field_shop":
        items = context.user_data.get('dict_shops', [])
        keyboard = create_paginated_keyboard(items, 0, 'eshop', 'shopName', 'shopID')
        # ИСПРАВЛЕНИЕ: Преобразуем кортеж в список перед добавлением
        new_rows = list(keyboard.inline_keyboard)
        new_rows.append([InlineKeyboardButton("🔙 Назад", callback_data="eshop_back")])
        await query.edit_message_text("Выберите магазин:", reply_markup=InlineKeyboardMarkup(new_rows))
        return EDITOR_SELECT_SHOP

    elif data == "edit_field_contractor":
        items = context.user_data.get('dict_contractors', [])
        keyboard = create_paginated_keyboard(items, 0, 'econtr', 'login', 'userID')
        # ИСПРАВЛЕНИЕ
        new_rows = list(keyboard.inline_keyboard)
        new_rows.append([InlineKeyboardButton("🔙 Назад", callback_data="econtr_back")])
        await query.edit_message_text("Выберите подрядчика:", reply_markup=InlineKeyboardMarkup(new_rows))
        return EDITOR_SELECT_CONTRACTOR

    elif data == "edit_field_work":
        items = context.user_data.get('dict_works', [])
        keyboard = create_paginated_keyboard(items, 0, 'ework', 'workCategoryName', 'workCategoryID')
        # ИСПРАВЛЕНИЕ
        new_rows = list(keyboard.inline_keyboard)
        new_rows.append([InlineKeyboardButton("🔙 Назад", callback_data="ework_back")])
        await query.edit_message_text("Выберите вид работ:", reply_markup=InlineKeyboardMarkup(new_rows))
        return EDITOR_SELECT_WORK

    elif data == "edit_field_urgency":
        items = context.user_data.get('dict_urgencies', [])
        keyboard = create_paginated_keyboard(items, 0, 'eurg', 'urgencyName', 'urgencyID')
        # ИСПРАВЛЕНИЕ
        new_rows = list(keyboard.inline_keyboard)
        new_rows.append([InlineKeyboardButton("🔙 Назад", callback_data="eurg_back")])
        await query.edit_message_text("Выберите срочность:", reply_markup=InlineKeyboardMarkup(new_rows))
        return EDITOR_SELECT_URGENCY

    elif data == "edit_field_desc":
        current_desc = context.user_data['editor_draft'].get('description', '')

        # Сохраняем ID сообщения, которое стало "промптом"
        context.user_data['editor_prompt_message_id'] = query.message.message_id

        await query.edit_message_text(
            f"Текущее описание:\n<i>{escape_markdown(current_desc)}</i>\n\n"
            "Введите новое описание (текстом):",
            parse_mode=ParseMode.HTML
        )
        return EDITOR_INPUT_TEXT

    elif data == "edit_field_status":
        buttons = [
            [InlineKeyboardButton("В работе (In work)", callback_data="estatus_In work")],
            [InlineKeyboardButton("Выполнена (Done)", callback_data="estatus_Done")],
            [InlineKeyboardButton("Закрыта (Closed)", callback_data="estatus_Closed")],
            [InlineKeyboardButton("🔙 Назад", callback_data="estatus_back")]
        ]
        await query.edit_message_text("Выберите статус:", reply_markup=InlineKeyboardMarkup(buttons))
        return EDITOR_SELECT_STATUS

    return EDITOR_MAIN_MENU


# --- Helper для выбора из списка ---
async def _handle_selection(update: Update, context: Context,
                            prefix: str, list_key: str, id_key: str, name_key: str,
                            draft_id_key: str, draft_name_key: str, next_state: int):
    """Универсальный обработчик выбора из пагинированного списка."""
    query = update.callback_query
    await safe_answer_query(query)
    data = query.data

    if data == f"{prefix}_back":
        return await render_editor_menu(update, context)

    action, value = data.split('_', 2)[1:]  # eshop_page_1 или eshop_select_5

    if action == 'page':
        items = context.user_data.get(list_key, [])
        keyboard = create_paginated_keyboard(items, int(value), prefix, name_key, id_key)
        # ИСПРАВЛЕНИЕ: Преобразуем кортеж в список перед добавлением кнопки Назад
        new_rows = list(keyboard.inline_keyboard)
        new_rows.append([InlineKeyboardButton("🔙 Назад", callback_data=f"{prefix}_back")])

        await query.edit_message_reply_markup(reply_markup=InlineKeyboardMarkup(new_rows))
        return next_state

    elif action == 'select':
        selected_id = int(value)
        items = context.user_data.get(list_key, [])
        item = next((i for i in items if i[id_key] == selected_id), None)

        if item:
            # Сохраняем в черновик
            context.user_data['editor_draft'][draft_id_key] = selected_id
            context.user_data['editor_draft'][draft_name_key] = item[name_key]

            if list_key == 'dict_urgencies' and item['urgencyName'] == 'Customizable':
                # Сохраняем ID сообщения
                context.user_data['editor_prompt_message_id'] = query.message.message_id

                await query.edit_message_text("Введите количество дней (число от 1 до 365):")
                context.user_data['editor_waiting_custom_days'] = True
                return EDITOR_INPUT_TEXT

        return await render_editor_menu(update, context)

    return next_state


# --- Конкретные обработчики выбора ---

async def editor_select_shop(update: Update, context: Context) -> int:
    return await _handle_selection(update, context, 'eshop', 'dict_shops', 'shopID', 'shopName',
                                   'shopID', 'shopName', EDITOR_SELECT_SHOP)


async def editor_select_contractor(update: Update, context: Context) -> int:
    return await _handle_selection(update, context, 'econtr', 'dict_contractors', 'userID', 'login',
                                   'assignedContractorID', 'contractorName', EDITOR_SELECT_CONTRACTOR)


async def editor_select_work(update: Update, context: Context) -> int:
    return await _handle_selection(update, context, 'ework', 'dict_works', 'workCategoryID', 'workCategoryName',
                                   'workCategoryID', 'workCategoryName', EDITOR_SELECT_WORK)


async def editor_select_urgency(update: Update, context: Context) -> int:
    return await _handle_selection(update, context, 'eurg', 'dict_urgencies', 'urgencyID', 'urgencyName',
                                   'urgencyID', 'urgencyName', EDITOR_SELECT_URGENCY)


async def editor_select_status(update: Update, context: Context) -> int:
    query = update.callback_query
    await safe_answer_query(query)
    data = query.data

    if data == "estatus_back":
        return await render_editor_menu(update, context)

    status = data.split('_')[1]
    context.user_data['editor_draft']['status'] = status
    return await render_editor_menu(update, context)


# handlers.py

async def editor_input_text(update: Update, context: Context) -> int:
    text = update.message.text

    # 1. Удаляем сообщение пользователя (как и было)
    try:
        await update.message.delete()
    except:
        pass

    # 2. Удаляем сообщение бота с просьбой ввести текст (НОВОЕ)
    prompt_msg_id = context.user_data.pop('editor_prompt_message_id', None)
    if prompt_msg_id:
        try:
            await context.bot.delete_message(chat_id=update.effective_chat.id, message_id=prompt_msg_id)
        except Exception as e:
            logger.warning(f"Не удалось удалить промпт ввода: {e}")

    if context.user_data.get('editor_waiting_custom_days'):
        if text.isdigit() and 1 <= int(text) <= 365:
            context.user_data['editor_draft']['customDays'] = int(text)
            context.user_data['editor_waiting_custom_days'] = False
            return await render_editor_menu(update, context)
        else:
            msg = await update.message.reply_text("❌ Введите число от 1 до 365.")
            # Можно сохранить ID ошибки, чтобы удалить его при следующем вводе, но это уже опционально
            return EDITOR_INPUT_TEXT
    else:
        # Это ввод описания
        context.user_data['editor_draft']['description'] = text
        return await render_editor_menu(update, context)

async def _submit_editor_data(update: Update, context: Context) -> int:
    """Отправка данных на сервер."""
    query = update.callback_query
    draft = context.user_data['editor_draft']
    is_new = context.user_data['editor_is_new']

    await query.edit_message_text("⏳ Сохранение данных...", reply_markup=None)

    # Подготовка payload
    payload = {
        "description": draft['description'],
        "shopID": draft['shopID'],
        "workCategoryID": draft['workCategoryID'],
        "urgencyID": draft['urgencyID'],
        "assignedContractorID": draft['assignedContractorID']
    }

    if 'customDays' in draft:
        payload['customDays'] = draft['customDays']

    if is_new:
        payload['createdByUserID'] = draft['createdByUserID']
        response = await api_client.create_request(payload)
        success_msg = f"✅ Заявка создана! ID: {response.get('requestID')}" if response else "❌ Ошибка создания."
    else:
        payload['status'] = draft.get('status', 'In work')
        request_id = draft['requestID']
        response = await api_client.update_request(request_id, payload)
        success_msg = f"✅ Заявка #{request_id} обновлена!" if response else "❌ Ошибка обновления."

    if response:
        # Сброс кэша заявок
        context.user_data.pop('requests_cache', None)
        context.user_data.pop('requests_cache_key', None)

        # 1. Показываем результат БЕЗ кнопок
        await query.edit_message_text(
            success_msg,
            reply_markup=None
        )

        # 2. Создаем задачу на удаление сообщения через 10 секунд
        async def delayed_delete():
            try:
                await asyncio.sleep(10)
                await query.delete_message()
            except Exception as e:
                # Сообщение могло быть уже удалено пользователем или чат очищен
                logger.warning(f"Не удалось удалить сообщение об успехе: {e}")

        asyncio.create_task(delayed_delete())

    else:
        # Возвращаем в редактор при ошибке
        await query.answer("Произошла ошибка при сохранении", show_alert=True)
        return await render_editor_menu(update, context)

    return ConversationHandler.END