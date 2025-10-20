# api_client.py
import httpx
from config import BACKEND_URL, API_KEY

async def check_backend_health() -> str:
    api_url = f"{BACKEND_URL}/api/bot/health"
    headers = {"X-API-KEY": API_KEY}

    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(api_url, headers=headers)

            if response.status_code == 200:
                return f"✅ **Сервер доступен!**\nОтвет: `{response.json()}`"
            elif response.status_code == 401:
                return "❌ **Ошибка 401: Unauthorized.**\nСервер отклонил запрос. Проверьте правильность `BACKEND_API_KEY` в вашем `.env` файле."
            elif response.status_code == 403:
                return "❌ **Ошибка 403: Forbidden.**\nУбедитесь, что в `SecurityConfig.java` для роли `BOT` разрешен доступ к эндпоинтам `/api/bot/**`."
            else:
                return f"❌ **Ошибка сервера.**\nКод ответа: `{response.status_code}`.\nТело ответа: `{response.text}`"

        except httpx.ConnectError:
            return f"❌ **Ошибка подключения.**\nНе удалось связаться с сервером по адресу `{api_url}`. Убедитесь, что бэкенд запущен и доступен."
        except Exception as e:
            return f"❌ **Произошла непредвиденная ошибка при запросе:**\n`{e}`"