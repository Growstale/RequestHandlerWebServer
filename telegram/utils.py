from telegram import InlineKeyboardButton, InlineKeyboardMarkup

ITEMS_PER_PAGE = 8


def create_paginated_keyboard(items: list, page: int, data_prefix: str, name_key: str,
                              id_key: str) -> InlineKeyboardMarkup:

    keyboard = []
    start_index = page * ITEMS_PER_PAGE
    end_index = start_index + ITEMS_PER_PAGE

    for i in range(start_index, end_index, 2):
        row = []
        if i < len(items):
            item = items[i]
            row.append(InlineKeyboardButton(
                item[name_key],
                callback_data=f"{data_prefix}_select_{item[id_key]}"
            ))
        if i + 1 < len(items):
            item = items[i + 1]
            row.append(InlineKeyboardButton(
                item[name_key],
                callback_data=f"{data_prefix}_select_{item[id_key]}"
            ))
        if row:
            keyboard.append(row)

    nav_row = []
    if page > 0:
        nav_row.append(InlineKeyboardButton("⬅️ Назад", callback_data=f"{data_prefix}_page_{page - 1}"))

    total_pages = (len(items) + ITEMS_PER_PAGE - 1) // ITEMS_PER_PAGE
    if total_pages > 1:
        nav_row.append(InlineKeyboardButton(f"{page + 1}/{total_pages}", callback_data="noop"))

    if end_index < len(items):
        nav_row.append(InlineKeyboardButton("Вперед ➡️", callback_data=f"{data_prefix}_page_{page + 1}"))

    if nav_row:
        keyboard.append(nav_row)

    return InlineKeyboardMarkup(keyboard)