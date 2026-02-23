from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup, InputMediaPhoto, ReplyKeyboardMarkup, KeyboardButton
import datetime
import math
import asyncio
import html
from typing import Dict, Any, Coroutine, List, Tuple
from telegram.ext import ContextTypes, ConversationHandler, CallbackContext, ExtBot
from telegram.constants import ParseMode, ChatType
from telegram.error import BadRequest, TimedOut, RetryAfter
import api_client
from utils import create_paginated_keyboard
from bot_logging import logger
import time
from telegram import ForceReply


USER_LAST_ACTION_TIME = {}
COOLDOWN_SECONDS = 1.0
REPLY_COMMENT_SELECT = 29


def format_dt(iso_str: str) -> str:
    try:
        dt = datetime.datetime.fromisoformat(iso_str)
        return dt.strftime('%H:%M %d.%m.%Y')
    except:
        return iso_str


def check_rate_limit(user_id: int) -> bool:
    current_time = time.time()
    last_time = USER_LAST_ACTION_TIME.get(user_id, 0)

    if current_time - last_time < COOLDOWN_SECONDS:
        return False

    USER_LAST_ACTION_TIME[user_id] = current_time
    return True


class CustomContext(CallbackContext[ExtBot, Dict, Dict, Dict]):
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


async def delayed_delete_messages(context, chat_id, message_ids, delay=30):
    await asyncio.sleep(delay)
    for mid in message_ids:
        if mid:
            try:
                await context.bot.delete_message(chat_id=chat_id, message_id=mid)
            except Exception:
                pass


def escape_markdown(text: str) -> str:
    if not isinstance(text, str):
        return ""
    escape_chars = r'_*[]()~`>#+-=|{}.!-'
    return "".join(f"\\{char}" if char in escape_chars else char for char in text)


def format_request_list_item(req: dict) -> str:
    status_icon = "🟢" if req['status'] == 'Done' else ("⚪️" if req['status'] == 'In work' else "⚫️")
    overdue_icon = "❗️" if req['isOverdue'] else ""

    shop_name = escape_markdown(req['shopName'])

    raw_description = req.get('description', '')
    limit = 50
    if len(raw_description) > limit:
        description_text = raw_description[:limit] + "..."
    else:
        description_text = raw_description

    description = escape_markdown(description_text)

    return f"{status_icon} *ID {req['requestID']}*: {shop_name} {overdue_icon}\n_{description}_"


def format_request_details(req: dict) -> str:
    created_at_dt = datetime.datetime.fromisoformat(req['createdAt'])
    created_at = created_at_dt.strftime('%d.%m.%Y %H:%M')
    escaped_created_at = escape_markdown(created_at)

    deadline_info = ""
    if req.get('urgencyName') == 'Notes':
        deadline_info = "—"
    elif req['daysRemaining'] is not None:
        days_remaining_str = escape_markdown(str(req['daysRemaining']))
        deadline_info = f"{days_remaining_str} дн\\."
    else:
        deadline_info = "—"

    if req.get('isOverdue') and req.get('urgencyName') != 'Notes':
        deadline_info = f"Просрочено\\! \\({deadline_info}\\)"

    urgency_russian = get_urgency_ru(req.get('urgencyName', ''))

    executor = escape_markdown(req['assignedContractorName'] or 'Не назначен')
    urgency_val = escape_markdown(urgency_russian)
    if req.get('urgencyName') != 'Notes':
        days_for_task_str = escape_markdown(str(req['daysForTask']))
        urgency_display = f"{urgency_val} \\({days_for_task_str} дн\\.\\)"
    else:
        urgency_display = urgency_val

    text = (
        f"📝 *Заявка \\#{req['requestID']}*\n\n"
        f"*Магазин:* {escape_markdown(req['shopName'])}\n"
        f"*Исполнитель:* {executor}\n"
        f"*Вид работ:* {escape_markdown(req['workCategoryName'])}\n"
        f"*Срочность:* {urgency_display}\n"
        f"*Статус:* {escape_markdown(get_status_ru(req['status']))}\n"
        f"*Создана:* {escaped_created_at}\n"
        f"*Срок:* {deadline_info}\n\n"
        f"*Описание:*\n```\n{escape_markdown(req['description'])}\n```"
    )
    return text


def get_status_ru(status):
    statuses = {
        "In work": "В работе",
        "Done": "Выполнена",
        "Closed": "Закрыта"
    }
    return statuses.get(status, status)


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


async def _fetch_full_dataset(user_id: int, filters: Dict[str, Any], context: Context) -> List[dict] | None:
    base_filters = {k: v for k, v in filters.items() if k != 'page'}
    base_filters['size'] = API_BATCH_SIZE
    aggregated: List[dict] = []
    page = 0
    total_pages = 1
    chat_id = context.user_data.get('view_chat_id')
    while page < total_pages:
        base_filters['page'] = page
        response = await api_client.get_requests(user_id, base_filters, chat_id=chat_id)
        if isinstance(response, dict) and "error_message" in response:
            return response  # Возвращаем словарь с ошибкой дальше

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

    dataset = await _fetch_full_dataset(user_id, filters, context)

    if isinstance(dataset, dict) and "error_message" in dataset:
        return dataset

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
    if not check_rate_limit(update.effective_user.id): return ConversationHandler.END
    user_id = update.effective_user.id
    user_info = await api_client.get_user_by_telegram_id(user_id)
    if not user_info:
        await update.message.reply_text("❌ Ваш Telegram ID не найден в системе.")
        return ConversationHandler.END

    context.user_data['view_chat_id'] = update.effective_chat.id
    context.user_data['view_filters'] = {'archived': False, 'page': 0, 'sort': ['requestID,asc']}
    _invalidate_requests_cache(context)
    context.user_data['user_info'] = user_info

    placeholder_message = await update.message.reply_text("🔄 Загружаю список заявок...")
    context.user_data['main_message_id'] = placeholder_message.message_id

    return await render_main_view_menu(update, context)


async def render_main_view_menu(update: Update, context: Context, is_callback: bool = False) -> int:
    user_id = update.effective_user.id

    filters = context.user_data.get('view_filters', {})
    if not filters:
        filters = {'archived': False, 'page': 0, 'sort': ['requestID,asc']}
        context.user_data['view_filters'] = filters

    dataset = await _get_sorted_dataset(user_id, context)

    if isinstance(dataset, dict) and "error_message" in dataset:
        import json
        try:
            err_data = json.loads(dataset['error_message'])
            err_msg = err_data.get('message', dataset['error_message'])
        except:
            err_msg = dataset['error_message']

        error_text = f"🚫 *Ошибка доступа:*\n{escape_markdown(err_msg)}"

        keyboard = [[InlineKeyboardButton("❌ Закрыть", callback_data="view_exit")]]

        if is_callback and update.callback_query:
            await update.callback_query.edit_message_text(error_text, parse_mode=ParseMode.MARKDOWN_V2)
        else:
            await context.bot.send_message(chat_id=update.effective_chat.id, text=error_text, parse_mode=ParseMode.MARKDOWN_V2)
        return VIEW_MAIN_MENU

    if dataset is None:
        error_text = "❌ Ошибка связи с сервером."
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
    if not check_rate_limit(update.effective_user.id):
        await query.answer("⚠️ Слишком часто!", show_alert=False)
        return VIEW_MAIN_MENU
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


async def view_sort_callback(update: Update, context: Context) -> int:
    query = update.callback_query
    if not check_rate_limit(update.effective_user.id):
        await query.answer("⚠️ Подождите...", show_alert=False)
        return VIEW_SET_SORTING
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
    if not check_rate_limit(update.effective_user.id): return VIEW_SET_SEARCH_TERM
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
    if role == 'RetailAdmin':
        second_action_row.append(InlineKeyboardButton("✏️ Изменить", callback_data=f"act_edit_{request_id}"))
    if role == 'Contractor' and status == 'In work':
        second_action_row.append(InlineKeyboardButton("✅ Завершить", callback_data=f"act_complete_{request_id}"))
    if second_action_row: keyboard.append(second_action_row)

    keyboard.append([InlineKeyboardButton("◀️ Назад к списку", callback_data="act_back_list")])

    await update.message.reply_text(message_text, reply_markup=InlineKeyboardMarkup(keyboard),
                                    parse_mode=ParseMode.MARKDOWN_V2)
    return VIEW_DETAILS


async def action_callback_handler(update: Update, context: Context) -> int | None:
    query = update.callback_query
    if not check_rate_limit(update.effective_user.id):
        await query.answer("⚠️ Подождите...", show_alert=False)
        return VIEW_DETAILS

    await safe_answer_query(query)
    data = query.data

    # 1. Сначала обрабатываем простые строковые команды
    if data == "act_back_list":
        class FakeUpdate:
            def __init__(self, q):
                self.callback_query = q
                self.effective_chat = q.message.chat
                self.effective_user = q.from_user
        return await render_main_view_menu(FakeUpdate(query), context, is_callback=True)

    if "back_to_request" in data:
        req_id = int(data.split('_')[-1])
        return await show_request_details_in_message(query, context, req_id)

    # 2. Обработка удаления и ответов
    if data.startswith('start_reply_cmt_'): return await start_reply_comment_handler(update, context)
    if data.startswith('start_del_cmt_'): return await start_delete_comment_handler(update, context)
    if data.startswith('start_del_img_'): return await start_delete_photo_handler(update, context)

    # 3. Обработка параметризованных действий act_...
    if data.startswith('act_'):
        parts = data.split('_')
        # Безопасный парсинг req_id (последний элемент, если он число)
        if parts[-1].isdigit():
            req_id = int(parts[-1])
        else:
            return None

        # Определение действия по количеству частей или по ключевым словам
        if "add_photo" in data:
            context.user_data['current_request_id'] = req_id
            sent_msg = await context.bot.send_message(
                chat_id=update.effective_chat.id,
                text="📤 <b>Отправьте одно или несколько фото</b> для заявки.\n\n"
                     "<i>Вы можете отправить их группой (альбомом). Как только загрузка закончится, меню обновится.</i>",
                parse_mode=ParseMode.HTML
            )
            context.user_data['photo_prompt_message_id'] = sent_msg.message_id
            return VIEW_ADD_PHOTO

        elif "add_comment" in data:
            parent_id = int(parts[4]) if len(parts) > 4 else None
            context.user_data['current_request_id'] = req_id
            context.user_data['parent_comment_id'] = parent_id
            text = "💬 Введите текст вашего ОТВЕТА:" if parent_id else "💬 Введите текст КОММЕНТАРИЯ:"

            prompt_msg = await context.bot.send_message(chat_id=update.effective_chat.id, text=text,
                                                        reply_markup=ForceReply(selective=True))
            context.user_data['comment_prompt_msg_id'] = prompt_msg.message_id
            return VIEW_ADD_COMMENT

        elif parts[1] == 'complete':
            await complete_request_action(query, context, req_id)
            return VIEW_DETAILS

        elif parts[1] == 'comments':
            await show_comments(query, context, req_id)
            return VIEW_DETAILS

        elif parts[1] == 'photos':
            await show_photos(query, context, req_id)
            return VIEW_DETAILS

        elif parts[1] == 'edit':
            return await start_edit_request(update, context)

    return None


async def show_comments(query, context: Context, request_id: int):
    user_info = context.user_data.get('user_info') or await api_client.get_user_by_telegram_id(query.from_user.id)
    is_admin = user_info and user_info.get('roleName') == 'RetailAdmin'
    comments = await api_client.get_comments(request_id)

    if not comments:
        text = f"💬 *Комментарии к заявке \\#{request_id}*\n\n_Комментариев пока нет\\._"
        keyboard = [[InlineKeyboardButton("◀️ Назад к заявке", callback_data=f"act_back_to_request_{request_id}")]]
        await query.edit_message_text(text, reply_markup=InlineKeyboardMarkup(keyboard),
                                      parse_mode=ParseMode.MARKDOWN_V2)
        return

    text = f"💬 *Комментарии к заявке \\#{request_id}*\n\n"

    for c in comments:
        time_str = escape_markdown(format_dt(c['createdAt']))
        author = escape_markdown(c['userLogin'])
        msg_text = escape_markdown(c['commentText'])

        text += f"👤 *{author}* \\[{time_str}\\]:\n{msg_text}\n"

        for r in c.get('replies', []):
            r_time = escape_markdown(format_dt(r['createdAt']))
            r_author = escape_markdown(r['userLogin'])
            r_text = escape_markdown(r['commentText'])
            text += f"  ↳ 👤 *{r_author}* \\[{r_time}\\]: {r_text}\n"
        text += "\n"

    if len(text) > 4000:
        text = text[:3900] + "\n\\.\\.\\.\n_\\(сообщения слишком длинные, показана часть\\)_"

    keyboard = []
    keyboard.append([InlineKeyboardButton("↩️ Ответить на коммент", callback_data=f"start_reply_cmt_{request_id}")])

    if is_admin:
        keyboard.append([InlineKeyboardButton("🗑 Удалить комментарий", callback_data=f"start_del_cmt_{request_id}")])

    keyboard.append([InlineKeyboardButton("◀️ Назад к заявке", callback_data=f"act_back_to_request_{request_id}")])

    await query.edit_message_text(text, reply_markup=InlineKeyboardMarkup(keyboard), parse_mode=ParseMode.MARKDOWN_V2)


async def start_reply_comment_handler(update: Update, context: Context) -> int:
    query = update.callback_query
    await safe_answer_query(query)

    request_id = int(query.data.split('_')[-1])
    comments = await api_client.get_comments(request_id)

    if not comments:
        await query.answer("Нет комментариев")
        return VIEW_DETAILS

    keyboard = []
    for c in comments:
        snippet = c['commentText'][:25] + "..." if len(c['commentText']) > 25 else c['commentText']
        keyboard.append([InlineKeyboardButton(f"💬 {c['userLogin']}: {snippet}",
                                              callback_data=f"act_add_comment_{request_id}_{c['commentID']}")])

    keyboard.append([InlineKeyboardButton("🔙 Отмена", callback_data=f"act_comments_{request_id}")])

    await query.edit_message_text("Выберите комментарий, на который хотите ответить:",
                                  reply_markup=InlineKeyboardMarkup(keyboard))
    return REPLY_COMMENT_SELECT


async def start_delete_comment_handler(update: Update, context: Context) -> int:
    query = update.callback_query
    await safe_answer_query(query)

    request_id = int(query.data.split('_')[-1])
    comments = await api_client.get_comments(request_id)

    if not comments:
        await query.answer("Нет комментариев для удаления", show_alert=True)
        return VIEW_DETAILS

    keyboard = []
    for c in comments:
        snippet = c['commentText'][:20] + "..." if len(c['commentText']) > 20 else c['commentText']
        btn_text = f"{c['userLogin']}: {snippet}"
        keyboard.append([InlineKeyboardButton(btn_text, callback_data=f"conf_del_cmt_{c['commentID']}_{request_id}")])

    keyboard.append([InlineKeyboardButton("🔙 Отмена", callback_data=f"act_comments_{request_id}")])

    await query.edit_message_text(
        "Выберите комментарий для удаления:",
        reply_markup=InlineKeyboardMarkup(keyboard)
    )
    return DELETE_COMMENT_SELECT


async def confirm_delete_comment_handler(update: Update, context: Context) -> int:
    query = update.callback_query
    if not check_rate_limit(update.effective_user.id): return DELETE_COMMENT_SELECT
    await safe_answer_query(query)

    _, _, _, comment_id, request_id = query.data.split('_')
    comment_id = int(comment_id)
    request_id = int(request_id)

    all_comments = await api_client.get_comments(request_id)

    target_comment = None
    for c in all_comments:
        if c['commentID'] == comment_id:
            target_comment = c
            break

    if not target_comment:
        await query.edit_message_text("❌ Комментарий уже удален.")
        return VIEW_DETAILS

    has_children = len(target_comment.get('replies', [])) > 0

    if has_children and not query.data.startswith("force_del_cmt_"):
        child_count = len(target_comment['replies'])
        keyboard = [
            [InlineKeyboardButton(f"⚠️ Да, удалить всё ({child_count + 1} сообщ.)",
                                  callback_data=f"force_del_cmt_{comment_id}_{request_id}")],
            [InlineKeyboardButton("🔙 Отмена", callback_data=f"act_comments_{request_id}")]
        ]
        await query.edit_message_text(
            f"❓ *Внимание\\!*\n\nУ этого комментария есть ответы \\({child_count} шт\\.\\)\\.\n"
            f"Если вы его удалите, *вся ветка ответов* также исчезнет\\. Вы уверены?",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode=ParseMode.MARKDOWN_V2
        )
        return DELETE_COMMENT_SELECT

    await api_client.delete_comment(comment_id)
    await query.answer("✅ Комментарий и ответы удалены")

    await show_comments(query, context, request_id)
    return VIEW_DETAILS


async def show_photos(query, context: Context, request_id: int):
    user_info = context.user_data.get('user_info')
    if not user_info:
        user_info = await api_client.get_user_by_telegram_id(query.from_user.id)
        context.user_data['user_info'] = user_info

    photo_ids = await api_client.get_photo_ids(request_id)

    if not photo_ids:
        await safe_answer_query(query, text="Фотографий нет.", show_alert=True)
        if query.data.startswith("fin_del_img"):
            await show_request_details_in_message(query, context, request_id)
        return

    # Заменяем текущее меню на "Загружаю..."
    await query.edit_message_text(f"Загружаю {len(photo_ids)} фото...")

    media_group = []
    display_ids = photo_ids[-10:]

    for pid in display_ids:
        photo_bytes = await api_client.get_photo(pid)
        if photo_bytes:
            media_group.append(InputMediaPhoto(media=photo_bytes))

    sent_media_messages = []
    if media_group:
        sent_media_messages = await query.message.reply_media_group(media=media_group)

    info_msg = await context.bot.send_message(
        chat_id=query.message.chat_id,
        text=f"🖼 Фотографии к заявке #{request_id} отправлены. (удалятся через 30 сек)"
    )

    # Собираем ID фотографий и информационного сообщения для удаления
    msgs_to_delete = [msg.message_id for msg in sent_media_messages]
    msgs_to_delete.append(info_msg.message_id)

    asyncio.create_task(delayed_delete_messages(context, query.message.chat_id, msgs_to_delete, 30))

    # Удаляем сообщение "Загружаю..."
    try:
        await query.message.delete()
    except:
        pass

    # Восстанавливаем меню заявки (появится сразу под фотографиями)
    await restore_request_menu(context, query.message.chat_id, query.from_user.id, request_id)


async def start_delete_photo_handler(update: Update, context: Context) -> int:
    query = update.callback_query
    request_id = int(query.data.split('_')[-1])

    photo_ids = await api_client.get_photo_ids(request_id)
    if not photo_ids:
        await query.answer("Нет фото для удаления", show_alert=True)
        return VIEW_DETAILS

    keyboard = []
    row = []
    for idx, pid in enumerate(photo_ids, start=1):
        row.append(InlineKeyboardButton(f"Фото {idx}", callback_data=f"preview_del_img_{pid}_{request_id}"))
        if len(row) == 3:
            keyboard.append(row)
            row = []
    if row:
        keyboard.append(row)

    keyboard.append([InlineKeyboardButton("🔙 Отмена", callback_data=f"act_photos_{request_id}")])

    await query.edit_message_text(
        "Выберите номер фото для удаления (чтобы увидеть превью и подтвердить):",
        reply_markup=InlineKeyboardMarkup(keyboard)
    )
    return DELETE_PHOTO_SELECT


async def preview_delete_photo_handler(update: Update, context: Context) -> int:
    query = update.callback_query
    _, _, _, photo_id, request_id = query.data.split('_')

    await query.delete_message()

    photo_bytes = await api_client.get_photo(int(photo_id))

    keyboard = [
        [InlineKeyboardButton("❌ УДАЛИТЬ ЭТО ФОТО", callback_data=f"fin_del_img_{photo_id}_{request_id}")],
        [InlineKeyboardButton("🔙 Отмена (назад к списку)", callback_data=f"start_del_img_{request_id}")]
    ]

    await context.bot.send_photo(
        chat_id=update.effective_chat.id,
        photo=photo_bytes,
        caption=f"Удалить это фото из заявки #{request_id}?",
        reply_markup=InlineKeyboardMarkup(keyboard)
    )
    return DELETE_PHOTO_SELECT


async def finalize_delete_photo_handler(update: Update, context: Context) -> int:
    query = update.callback_query
    if not check_rate_limit(update.effective_user.id): return DELETE_PHOTO_SELECT
    _, _, _, photo_id, request_id = query.data.split('_')

    await api_client.delete_photo(int(photo_id))
    await query.answer("Фото удалено")

    try:
        await query.delete_message()
    except:
        pass

    msg = await context.bot.send_message(
        chat_id=update.effective_chat.id,
        text="🔄 Обновляю список фото..."
    )

    class FakeQuery:
        def __init__(self, original_user, message_obj, bot):
            self.from_user = original_user
            self.message = message_obj
            self.data = "fake_data"
            self._bot = bot

        async def edit_message_text(self, text, reply_markup=None, parse_mode=None):
            await self._bot.edit_message_text(
                chat_id=self.message.chat_id,
                message_id=self.message.message_id,
                text=text,
                reply_markup=reply_markup,
                parse_mode=parse_mode
            )

        async def answer(self, *args, **kwargs):
            pass


    fake_query = FakeQuery(query.from_user, msg, context.bot)

    await show_photos(fake_query, context, int(request_id))

    return VIEW_DETAILS


async def add_comment_handler(update: Update, context: Context) -> int:
    if not check_rate_limit(update.effective_user.id):
        return VIEW_ADD_COMMENT

    comment_text = update.message.text or update.message.caption
    if not comment_text:
        await update.message.reply_text("⚠️ Пожалуйста, введите текст комментария.")
        return VIEW_ADD_COMMENT

    request_id = context.user_data.get('current_request_id')
    parent_id = context.user_data.pop('parent_comment_id', None)
    user_id = update.effective_user.id

    if not request_id:
        await update.message.reply_text("❌ Ошибка: сессия потеряна. Откройте заявку заново.")
        return ConversationHandler.END

    try:
        await update.message.delete()
    except:
        pass

    response = await api_client.add_comment(request_id, user_id, comment_text, parent_id)

    msgs_to_delete = []

    # Добавляем в список удаления сообщение-промпт (💬 Введите текст...)
    prompt_id = context.user_data.pop('comment_prompt_msg_id', None)
    if prompt_id:
        msgs_to_delete.append(prompt_id)

    if response:
        success_msg = await context.bot.send_message(
            chat_id=update.effective_chat.id,
            text=f"✅ Комментарий к заявке #{request_id} добавлен. (удалится через 30 сек)"
        )
        msgs_to_delete.append(success_msg.message_id)
    else:
        err_msg = await context.bot.send_message(
            chat_id=update.effective_chat.id,
            text="❌ Ошибка бэкенда при сохранении комментария."
        )
        msgs_to_delete.append(err_msg.message_id)

    # Запускаем таймер на удаление
    asyncio.create_task(delayed_delete_messages(context, update.effective_chat.id, msgs_to_delete, 30))

    _invalidate_requests_cache(context)
    await restore_request_menu(context, update.effective_chat.id, user_id, request_id)
    return VIEW_DETAILS


async def add_photo_handler(update: Update, context: Context) -> int:
    current_time = time.time()
    if current_time - USER_LAST_ACTION_TIME.get(update.effective_user.id, 0) < 0.5: return VIEW_ADD_PHOTO

    request_id = context.user_data.get('current_request_id')
    user_id = update.effective_user.id

    if not request_id:
        await update.message.reply_text("❌ Ошибка: не найдена заявка.")
        return VIEW_MAIN_MENU

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

    try:
        await update.message.delete()
    except Exception:
        pass

    photo_prompt_id = context.user_data.get('photo_prompt_message_id')
    if photo_prompt_id:
        try:
            await context.bot.delete_message(chat_id=update.effective_chat.id, message_id=photo_prompt_id)
        except Exception:
            pass
        context.user_data.pop('photo_prompt_message_id', None)

    media_group_id = update.message.media_group_id

    if not media_group_id:
        return await finalize_photo_upload(context, update.effective_chat.id, user_id, request_id, [photo_bytes])

    if 'upload_buffer' not in context.user_data:
        context.user_data['upload_buffer'] = {}

    if media_group_id not in context.user_data['upload_buffer']:
        context.user_data['upload_buffer'][media_group_id] = []
        asyncio.create_task(process_media_group(context, media_group_id, update.effective_chat.id, user_id, request_id))

    context.user_data['upload_buffer'][media_group_id].append(photo_bytes)

    return VIEW_ADD_PHOTO


async def process_media_group(context, media_group_id, chat_id, user_id, request_id):
    await asyncio.sleep(2)

    buffer = context.user_data.get('upload_buffer', {}).pop(media_group_id, [])
    if not buffer:
        return

    await finalize_photo_upload(context, chat_id, user_id, request_id, buffer)


async def finalize_photo_upload(context, chat_id, user_id, request_id, photos):
    req_details = await api_client.get_request_details(user_id, request_id)

    if req_details:
        current_count = req_details.get('photoCount', 0)
        incoming_count = len(photos)

        if current_count + incoming_count > 10:
            error_msg = await context.bot.send_message(
                chat_id=chat_id,
                text=f"❌ Ошибка: Лимит 10 фото. Уже загружено: {current_count}. Пытались добавить: {incoming_count}."
            )
            asyncio.create_task(delayed_delete(context, chat_id, error_msg.message_id, 5))

            await restore_request_menu(context, chat_id, user_id, request_id)
            return VIEW_DETAILS

    success = await api_client.upload_photos(request_id, user_id, photos)

    if success:
        _invalidate_requests_cache(context)
    else:
        await context.bot.send_message(chat_id=chat_id, text=f"❌ Не удалось загрузить фото для заявки #{request_id}.")

    await restore_request_menu(context, chat_id, user_id, request_id)
    return VIEW_DETAILS


async def restore_request_menu(context, chat_id, user_id, request_id):
    try:
        user_info = context.user_data.get('user_info') or await api_client.get_user_by_telegram_id(user_id)
        req_details = await api_client.get_request_details(user_id, request_id)

        if not user_info or not req_details:
            await context.bot.send_message(chat_id=chat_id, text="⚠️ Не удалось обновить меню заявки. Попробуйте ввести /start или открыть список заявок заново.")
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
            action_row.append(InlineKeyboardButton(f"🖼️ Фото ({req_details['photoCount']})",
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

    except Exception as e:
        logger.error(f"Error restoring menu: {e}")
        await context.bot.send_message(chat_id=chat_id, text="⚠️ Произошла ошибка при отображении меню.")


async def delayed_delete(context, chat_id, message_id, delay=5):
    await asyncio.sleep(delay)
    try:
        await context.bot.delete_message(chat_id=chat_id, message_id=message_id)
    except Exception:
        pass


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

        if chat_info and isinstance(chat_info, dict) and "shopID" in chat_info:
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

    if not isinstance(urgencies, list):
        await update.effective_message.reply_text("❌ Не удалось загрузить категории срочности.")
        return ConversationHandler.END

    for u in urgencies:
        u['urgencyName'] = get_urgency_ru(u['urgencyName'])

    context.user_data['urgencies'] = urgencies
    keyboard = create_paginated_keyboard(context.user_data['urgencies'], 0, 'urgency', 'urgencyName', 'urgencyID')

    await context.bot.send_message(
        update.effective_chat.id,
        "<b>Шаг 4/5:</b> Выберите срочность:",
        reply_markup=keyboard,
        parse_mode=ParseMode.HTML
    )
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
    if not check_rate_limit(update.effective_user.id): return
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
    EDITOR_MAIN_MENU,
    EDITOR_SELECT_SHOP,
    EDITOR_SELECT_CONTRACTOR,
    EDITOR_SELECT_WORK,
    EDITOR_SELECT_URGENCY,
    EDITOR_INPUT_TEXT,
    EDITOR_SELECT_STATUS,
    DELETE_COMMENT_SELECT,
    DELETE_PHOTO_SELECT
) = range(20, 29)


URGENCY_TRANSLATIONS = {
    "Emergency": "Аварийная",
    "Urgent": "Срочная",
    "Planned": "Плановая",
    "Customizable": "Настраиваемая",
    "Notes": "Заметки"
}


def get_urgency_ru(name: str) -> str:
    return URGENCY_TRANSLATIONS.get(name, name)


def _get_editor_keyboard(draft: dict, is_new: bool, role: str) -> InlineKeyboardMarkup:
    buttons = []

    if not draft.get('shopID'):
        buttons.append([InlineKeyboardButton("❌ Выбрать Магазин", callback_data="edit_field_shop")])
    else:
        buttons.append([InlineKeyboardButton(f"🏪 Магазин: {draft['shopName']}", callback_data="noop")])

    if not draft.get('assignedContractorID'):
        buttons.append([InlineKeyboardButton("❌ Выбрать Исполнителя", callback_data="edit_field_contractor")])
    else:
        buttons.append([InlineKeyboardButton(f"👷 Исполнитель: {draft['contractorName']}", callback_data="noop")])

    work_ico = "✅" if draft.get('workCategoryID') else "❌"
    urg_ico = "✅" if draft.get('urgencyID') else "❌"
    desc_ico = "✅" if draft.get('description') else "❌"

    buttons.append([InlineKeyboardButton(f"{work_ico} Вид работ", callback_data="edit_field_work")])
    buttons.append([InlineKeyboardButton(f"{urg_ico} Срочность", callback_data="edit_field_urgency")])
    buttons.append([InlineKeyboardButton(f"{desc_ico} Описание", callback_data="edit_field_desc")])

    if not is_new:
        status_label = draft.get('status', 'In work')
        buttons.append([InlineKeyboardButton(f"Статус: {status_label}", callback_data="edit_field_status")])

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
    draft = context.user_data.get('editor_draft', {})
    is_new = context.user_data.get('editor_is_new', True)
    user_info = context.user_data.get('user_info', {})

    text = f"🛠 <b>{'СОЗДАНИЕ' if is_new else 'РЕДАКТИРОВАНИЕ'} ЗАЯВКИ</b>\n\n"

    # Используем html.escape вместо escape_markdown для HTML-парсмода
    shop_name = html.escape(draft.get('shopName', '--- Не выбрано ---'))
    contr_name = html.escape(draft.get('contractorName', '--- Не выбрано ---'))
    work_name = html.escape(draft.get('workCategoryName', '--- Не выбрано ---'))

    urg_raw = draft.get('urgencyName')
    urg_name = html.escape(get_urgency_ru(urg_raw) if urg_raw else '--- Не выбрано ---')

    if draft.get('customDays'):
        urg_name += f" ({draft['customDays']} дн.)"

    desc = draft.get('description', '--- Не заполнено ---')
    if desc is None: desc = '--- Не заполнено ---'

    text += f"🏪 <b>Магазин:</b> {shop_name}\n"
    text += f"👷 <b>Исполнитель:</b> {contr_name}\n"
    text += f"📋 <b>Вид работ:</b> {work_name}\n"
    text += f"🔥 <b>Срочность:</b> {urg_name}\n"
    # Экранируем описание только после обрезки
    short_desc = html.escape(desc[:100])
    text += f"📝 <b>Описание:</b>\n<i>{short_desc}{'...' if len(desc) > 100 else ''}</i>\n"

    if not is_new:
        text += f"\n📊 <b>Статус:</b> {html.escape(draft.get('status', 'In work'))}"

    keyboard = _get_editor_keyboard(draft, is_new, user_info.get('roleName'))

    try:
        if update.callback_query:
            await update.callback_query.edit_message_text(text, reply_markup=keyboard, parse_mode=ParseMode.HTML)
        else:
            await context.bot.send_message(chat_id=update.effective_chat.id, text=text, reply_markup=keyboard,
                                           parse_mode=ParseMode.HTML)
    except BadRequest as e:
        # Теперь мы увидим ошибку в логах, если она возникнет
        logger.error(f"Ошибка отрисовки меню редактора: {e}")
        if update.callback_query:
            await update.callback_query.answer("⚠️ Ошибка отображения меню", show_alert=True)

    return EDITOR_MAIN_MENU


async def start_create_request(update: Update, context: Context) -> int:
    if not check_rate_limit(update.effective_user.id):
        return ConversationHandler.END

    user_id = update.effective_user.id
    chat = update.effective_chat

    user_data = await api_client.get_user_by_telegram_id(user_id)
    if not user_data or user_data.get("roleName") != "RetailAdmin":
        await update.message.reply_text("❌ Только администратор может создавать заявки.")
        return ConversationHandler.END

    draft = {
        'createdByUserID': user_data['userID'],
        'status': 'In work'
    }

    if chat.type in [ChatType.GROUP, ChatType.SUPERGROUP]:
        chat_info = await api_client.get_chat_info_by_telegram_id(chat.id)

        if not chat_info or (isinstance(chat_info, dict) and "error_message" in chat_info):
            await update.message.reply_text(
                "❌ Этот чат не зарегистрирован в системе.\n"
                "Создание заявок разрешено только в привязанных чатах магазинов или в личке с ботом."
            )
            return ConversationHandler.END

        draft['shopID'] = chat_info['shopID']
        draft['shopName'] = chat_info['shopName']

        if chat_info.get('contractorID'):
            draft['assignedContractorID'] = chat_info['contractorID']
            draft['contractorName'] = chat_info['contractorLogin']

    else:
        pass

    context.user_data['user_info'] = user_data
    context.user_data['editor_is_new'] = True
    context.user_data['editor_draft'] = draft

    await _preload_dictionaries(context)

    return await render_editor_menu(update, context)


async def start_edit_request(update: Update, context: Context) -> int:
    query = update.callback_query

    try:
        parts = query.data.split('_')
        request_id = int(parts[-1])
    except (IndexError, ValueError):
        await query.answer("Ошибка ID заявки", show_alert=True)
        return VIEW_MAIN_MENU

    user_id = update.effective_user.id

    await query.answer("Загружаю данные заявки...")

    req = await api_client.get_request_details(user_id, request_id)
    if not req:
        await query.edit_message_text("❌ Не удалось загрузить данные заявки. Возможно, она была удалена.")
        return VIEW_MAIN_MENU

    user_data = await api_client.get_user_by_telegram_id(user_id)
    if not user_data:
        return VIEW_MAIN_MENU

    context.user_data['user_info'] = user_data

    context.user_data['editor_is_new'] = False

    context.user_data['editor_draft'] = {
        'requestID': req['requestID'],
        'description': req['description'],

        'shopID': req['shopID'],
        'shopName': req['shopName'],

        'workCategoryID': req['workCategoryID'],
        'workCategoryName': req['workCategoryName'],

        'urgencyID': req['urgencyID'],
        'urgencyName': req['urgencyName'],

        'assignedContractorID': req['assignedContractorID'],
        'contractorName': req['assignedContractorName'] if req['assignedContractorName'] else "Не назначен",

        'status': req['status'],
        'daysForTask': req['daysForTask']
    }

    if req['urgencyName'] == 'Customizable':
        context.user_data['editor_draft']['customDays'] = req['daysForTask']

    await _preload_dictionaries(context)

    return await render_editor_menu(update, context)


async def _preload_dictionaries(context: Context):
    shops = await api_client.get_all_shops()
    contractors = await api_client.get_all_contractors()
    works = await api_client.get_all_work_categories()
    urgencies = await api_client.get_all_urgency_categories()
    context.user_data['dict_shops'] = shops.get('content', []) if (shops and isinstance(shops, dict)) else []
    context.user_data['dict_contractors'] = contractors if isinstance(contractors, list) else []
    context.user_data['dict_works'] = works.get('content', []) if (works and isinstance(works, dict)) else []

    if isinstance(urgencies, list):
        for u in urgencies:
            u['urgencyName'] = get_urgency_ru(u['urgencyName'])
        context.user_data['dict_urgencies'] = urgencies
    else:
        context.user_data['dict_urgencies'] = []


async def editor_main_callback(update: Update, context: Context) -> int:
    query = update.callback_query
    if not check_rate_limit(update.effective_user.id):
        await query.answer("⚠️ Подождите...", show_alert=False); return EDITOR_MAIN_MENU
    await safe_answer_query(query)
    data = query.data

    if data == "editor_cancel":
        await query.delete_message()
        return ConversationHandler.END

    elif data == "editor_save":
        return await _submit_editor_data(update, context)

    elif data == "edit_field_shop":
        items = context.user_data.get('dict_shops', [])
        keyboard = create_paginated_keyboard(items, 0, 'eshop', 'shopName', 'shopID')
        new_rows = list(keyboard.inline_keyboard)
        new_rows.append([InlineKeyboardButton("🔙 Назад", callback_data="eshop_back")])
        await query.edit_message_text("Выберите магазин:", reply_markup=InlineKeyboardMarkup(new_rows))
        return EDITOR_SELECT_SHOP

    elif data == "edit_field_contractor":
        items = context.user_data.get('dict_contractors', [])
        keyboard = create_paginated_keyboard(items, 0, 'econtr', 'login', 'userID')
        new_rows = list(keyboard.inline_keyboard)
        new_rows.append([InlineKeyboardButton("🔙 Назад", callback_data="econtr_back")])
        await query.edit_message_text("Выберите подрядчика:", reply_markup=InlineKeyboardMarkup(new_rows))
        return EDITOR_SELECT_CONTRACTOR

    elif data == "edit_field_work":
        items = context.user_data.get('dict_works', [])
        keyboard = create_paginated_keyboard(items, 0, 'ework', 'workCategoryName', 'workCategoryID')
        new_rows = list(keyboard.inline_keyboard)
        new_rows.append([InlineKeyboardButton("🔙 Назад", callback_data="ework_back")])
        await query.edit_message_text("Выберите вид работ:", reply_markup=InlineKeyboardMarkup(new_rows))
        return EDITOR_SELECT_WORK

    elif data == "edit_field_urgency":

        items = context.user_data.get('dict_urgencies', [])
        keyboard = create_paginated_keyboard(items, 0, 'eurg', 'urgencyName', 'urgencyID')
        new_rows = list(keyboard.inline_keyboard)
        new_rows.append([InlineKeyboardButton("🔙 Назад", callback_data="eurg_back")])
        await query.edit_message_text("Выберите срочность:", reply_markup=InlineKeyboardMarkup(new_rows))
        return EDITOR_SELECT_URGENCY

    elif data == "edit_field_desc":
        current_desc = context.user_data['editor_draft'].get('description', '')

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


async def _handle_selection(update: Update, context: Context,
                            prefix: str, list_key: str, id_key: str, name_key: str,
                            draft_id_key: str, draft_name_key: str, next_state: int):
    query = update.callback_query
    await safe_answer_query(query)
    data = query.data

    if data == f"{prefix}_back":
        return await render_editor_menu(update, context)

    action, value = data.split('_', 2)[1:]

    if action == 'page':
        items = context.user_data.get(list_key, [])
        keyboard = create_paginated_keyboard(items, int(value), prefix, name_key, id_key)
        new_rows = list(keyboard.inline_keyboard)
        new_rows.append([InlineKeyboardButton("🔙 Назад", callback_data=f"{prefix}_back")])

        await query.edit_message_reply_markup(reply_markup=InlineKeyboardMarkup(new_rows))
        return next_state

    elif action == 'select':
        selected_id = int(value)
        items = context.user_data.get(list_key, [])
        item = next((i for i in items if i[id_key] == selected_id), None)

        if item:
            context.user_data['editor_draft'][draft_id_key] = selected_id
            context.user_data['editor_draft'][draft_name_key] = item[name_key]

            if list_key == 'dict_urgencies' and item['urgencyName'] in ['Customizable', 'Настраиваемая']:
                context.user_data['editor_prompt_message_id'] = query.message.message_id

                await query.edit_message_text("Введите количество дней (число от 1 до 365):")
                context.user_data['editor_waiting_custom_days'] = True
                return EDITOR_INPUT_TEXT

        return await render_editor_menu(update, context)

    return next_state



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



async def editor_input_text(update: Update, context: Context) -> int:
    if not check_rate_limit(update.effective_user.id): return EDITOR_INPUT_TEXT

    text = update.message.text

    try:
        await update.message.delete()
    except:
        pass

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
            return EDITOR_INPUT_TEXT
    else:
        context.user_data['editor_draft']['description'] = text
        return await render_editor_menu(update, context)


async def _submit_editor_data(update: Update, context: Context) -> int:
    query = update.callback_query
    # Важно: даем пользователю фидбек сразу
    await query.answer("⏳ Сохранение...")

    draft = context.user_data.get('editor_draft', {})
    is_new = context.user_data.get('editor_is_new', True)

    await query.edit_message_text("⏳ Отправка данных на сервер...", reply_markup=None)

    payload = {
        "description": draft.get('description'),
        "shopID": draft.get('shopID'),
        "workCategoryID": draft.get('workCategoryID'),
        "urgencyID": draft.get('urgencyID'),
        "assignedContractorID": draft.get('assignedContractorID')
    }
    if 'customDays' in draft:
        payload['customDays'] = draft['customDays']

    if is_new:
        payload['createdByUserID'] = draft.get('createdByUserID')
        response = await api_client.create_request(payload)
    else:
        payload['status'] = draft.get('status', 'In work')
        request_id = draft.get('requestID')
        response = await api_client.update_request(request_id, payload)

    # ИСПРАВЛЕННАЯ ПРОВЕРКА: успех может быть словарем (Create) или True (Update)
    is_success = False
    error_detail = "Неизвестная ошибка"

    if isinstance(response, dict):
        if "error_message" in response:
            error_detail = response["error_message"]
        else:
            is_success = True
    elif response is True:
        is_success = True

    if is_success:
        req_id = response.get('requestID') if isinstance(response, dict) else draft.get('requestID')
        success_text = f"✅ Заявка #{req_id} успешно {'создана' if is_new else 'обновлена'}! (сообщение удалится через 5 сек)"

        # Удаляем меню редактора
        try:
            await query.delete_message()
        except:
            pass

        # Отправляем сообщение об успехе НОВЫМ сообщением
        success_msg = await context.bot.send_message(chat_id=update.effective_chat.id, text=success_text)

        # Сбрасываем кэш, чтобы список заявок обновился
        context.user_data.pop('requests_cache', None)
        context.user_data.pop('requests_cache_key', None)

        # Подготавливаем контекст для возврата в главное меню
        if 'view_filters' not in context.user_data:
            context.user_data['view_filters'] = {'archived': False, 'page': 0, 'sort': ['requestID,asc']}

        # Удаляем ID старого сообщения, чтобы бот прислал меню отдельным сообщением
        context.user_data.pop('main_message_id', None)

        # Вызываем список заявок
        await render_main_view_menu(update, context, is_callback=False)

        # Удаляем сообщение об успехе через 5 секунд
        asyncio.create_task(delayed_delete_messages(context, update.effective_chat.id, [success_msg.message_id], 5))

        return ConversationHandler.END
    else:
        # Если произошла ошибка, возвращаемся в меню
        await query.answer(f"❌ Ошибка: {error_detail}", show_alert=True)
        return await render_editor_menu(update, context)


def get_main_menu_keyboard() -> ReplyKeyboardMarkup:
    keyboard = [
        [KeyboardButton("📋 Мои заявки"), KeyboardButton("➕ Новая заявка")]
    ]
    return ReplyKeyboardMarkup(keyboard, resize_keyboard=True, one_time_keyboard=False)

async def start_command(update: Update, context: CallbackContext):
    if not check_rate_limit(update.effective_user.id): return
    user = update.effective_user
    await update.message.reply_html(
        f"Привет, {user.mention_html()}!\n\n"
        "Воспользуйтесь меню внизу для управления заявками.",
        reply_markup=get_main_menu_keyboard()
    )

async def cancel_command(update: Update, context: CallbackContext) -> int:
    await update.message.reply_text(
        "Действие отменено.",
        reply_markup=get_main_menu_keyboard()
    )
    context.user_data.clear()
    return ConversationHandler.END

async def refresh_command(update: Update, context: CallbackContext):
    if not check_rate_limit(update.effective_user.id): return
    await start_command(update, context)
