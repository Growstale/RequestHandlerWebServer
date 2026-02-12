import httpx
import io
from config import BACKEND_URL, API_KEY
import logging

logger = logging.getLogger(__name__)


async def _make_request(method: str, endpoint: str, **kwargs):
    api_url = f"{BACKEND_URL}{endpoint}"
    headers = {"X-API-KEY": API_KEY, "Content-Type": "application/json"}

    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            response = await client.request(method, api_url, headers=headers, **kwargs)
            response.raise_for_status()

            if response.status_code == 204:
                return True

            return response.json()
        except httpx.HTTPStatusError as e:
            logger.error(f"HTTP Error for {e.request.url}: {e.response.status_code} - {e.response.text}")
            return None
        except httpx.RequestError as e:
            logger.error(f"Request Error for {e.request.url}: {e}")
            return None
        except Exception as e:
            logger.error(f"An unexpected error occurred: {e}")
            return None


async def get_user_by_telegram_id(telegram_id: int):
    return await _make_request("GET", f"/api/bot/user/telegram/{telegram_id}")


async def get_chat_info_by_telegram_id(chat_id: int):
    return await _make_request("GET", f"/api/bot/chat/{chat_id}")


async def get_all_shops():
    return await _make_request("GET", "/api/shops?size=1000")


async def get_all_contractors():
    return await _make_request("GET", "/api/user/contractors")


async def get_all_work_categories():
    return await _make_request("GET", "/api/work-categories?size=1000")


async def get_all_urgency_categories():
    return await _make_request("GET", "/api/urgency-categories")


async def create_request(request_data: dict):
    return await _make_request("POST", "/api/bot/requests", json=request_data)


def _normalize_query_params(params: dict):
    items = []
    for key, value in params.items():
        if value is None:
            continue
        if isinstance(value, list):
            for item in value:
                if item is not None:
                    items.append((key, str(item)))
        elif isinstance(value, bool):
            items.append((key, str(value).lower()))
        else:
            items.append((key, str(value)))
    return items


async def get_requests(telegram_id: int, params: dict):
    params = params.copy()
    params['telegram_id'] = telegram_id
    from httpx import QueryParams
    query_params = QueryParams(_normalize_query_params(params))
    return await _make_request("GET", "/api/bot/requests", params=query_params)


async def get_request_details(telegram_id: int, request_id: int):
    params = {'telegram_id': telegram_id}
    return await _make_request("GET", f"/api/bot/requests/{request_id}", params=params)


async def complete_request(telegram_id: int, request_id: int):
    data = {'telegram_id': telegram_id}
    return await _make_request("PUT", f"/api/bot/requests/{request_id}/complete", json=data)


async def get_comments(request_id: int):
    return await _make_request("GET", f"/api/bot/requests/{request_id}/comments")


async def add_comment(request_id: int, telegram_id: int, text: str, parent_id: int = None):
    data = {
        'telegram_id': telegram_id,
        'commentText': text,
        'parentCommentID': parent_id
    }
    return await _make_request("POST", f"/api/bot/requests/{request_id}/comments", json=data)


async def get_photo_ids(request_id: int):
    return await _make_request("GET", f"/api/bot/requests/{request_id}/photos/ids")


def get_photo_url(photo_id: int) -> str:
    return f"{BACKEND_URL}/api/requests/photos/{photo_id}"


async def upload_photos(request_id: int, telegram_id: int, photo_files: list):
    api_url = f"{BACKEND_URL}/api/bot/requests/{request_id}/photos"
    headers = {"X-API-KEY": API_KEY}
    params = {"telegram_id": telegram_id}

    files = []
    for i, photo_data in enumerate(photo_files):
        file_obj = io.BytesIO(photo_data)
        files.append(("files", (f"photo_{i}.jpg", file_obj, "image/jpeg")))

    async with httpx.AsyncClient(timeout=30.0) as client:
        try:
            response = await client.post(api_url, headers=headers, files=files, params=params)
            response.raise_for_status()
            return True
        except httpx.HTTPStatusError as e:
            logger.error(f"HTTP Error uploading photos: {e.response.status_code} - {e.response.text}")
            return False
        except httpx.RequestError as e:
            logger.error(f"Request Error uploading photos: {e}")
            return False
        except Exception as e:
            logger.error(f"An unexpected error uploading photos: {e}")
            return False


async def get_photo(photo_id: int):
    api_url = f"{BACKEND_URL}/api/requests/photos/{photo_id}"
    headers = {"X-API-KEY": API_KEY}

    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            response = await client.get(api_url, headers=headers)
            response.raise_for_status()
            return response.content
        except Exception as e:
            logger.error(f"Error fetching photo {photo_id}: {e}")
            return None


async def update_request(request_id: int, request_data: dict):
    return await _make_request("PUT", f"/api/bot/requests/{request_id}", json=request_data)


async def delete_comment(comment_id: int):
    return await _make_request("DELETE", f"/api/bot/requests/comments/{comment_id}")


async def delete_photo(photo_id: int):
    return await _make_request("DELETE", f"/api/bot/requests/photos/{photo_id}")
