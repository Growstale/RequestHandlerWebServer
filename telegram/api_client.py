import httpx
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