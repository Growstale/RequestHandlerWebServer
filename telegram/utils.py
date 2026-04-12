from telegram import InlineKeyboardButton, InlineKeyboardMarkup
from PIL import Image
import io

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


def compress_image(image_bytes: bytearray) -> bytes:
    """Сжимает изображение до 1280px и конвертирует в JPEG 70% качества"""
    # Читаем байты в объект Pillow
    img = Image.open(io.BytesIO(image_bytes))

    # Конвертируем в RGB (необходимо для JPEG, если оригинал был PNG с прозрачностью)
    if img.mode in ("RGBA", "P"):
        img = img.convert("RGB")

    # Пропорциональное уменьшение (thumbnail не увеличивает маленькие фото)
    MAX_SIZE = (1280, 1280)
    img.thumbnail(MAX_SIZE, Image.Resampling.LANCZOS)

    # Сохраняем результат в буфер
    output = io.BytesIO()
    img.save(output, format="JPEG", quality=70, optimize=True)

    return output.getvalue()
