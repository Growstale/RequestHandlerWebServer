import axios from 'axios'
import { logger } from '../lib/logger'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080'

const api = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

const refreshApi = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

let onRefreshFail = () => {};
export const setOnRefreshFail = (handler) => {
  onRefreshFail = handler;
};


export function attachAccessToken(token) {
  if (token) api.defaults.headers.common['Authorization'] = `Bearer ${token}`
  else delete api.defaults.headers.common['Authorization']
}

let isRefreshing = false
let failedQueue = []

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) prom.reject(error)
    else prom.resolve(token)
  })
  failedQueue = []
}

api.interceptors.response.use(
  res => res,
  async err => {
    const originalReq = err.config

    if (originalReq.url.includes('/auth/refresh')) {
      return Promise.reject(err);
    }

    if (axios.isCancel(err)) {
        return Promise.reject(err);
    }
    if (err.response && err.response.status === 401 && !originalReq._retry) {
      if (isRefreshing) {
        return new Promise(function (resolve, reject) {
          failedQueue.push({ resolve, reject })
        })
          .then(token => {
            originalReq.headers['Authorization'] = 'Bearer ' + token
            return api(originalReq)
          })
          .catch(err => Promise.reject(err))
      }

      originalReq._retry = true
      isRefreshing = true

      try {
        const refreshResp = await refreshApi.post('/api/auth/refresh')
        const newAccessToken = refreshResp.data.accessToken
        processQueue(null, newAccessToken)
        isRefreshing = false
        api.defaults.headers.common['Authorization'] = 'Bearer ' + newAccessToken
        originalReq.headers['Authorization'] = 'Bearer ' + newAccessToken
        return api(originalReq)
      } 
      catch (refreshErr) {
        logger.error('axios.interceptor on refresh', refreshErr)
        processQueue(refreshErr, null)
        onRefreshFail()
        return Promise.reject(refreshErr)
      }
      finally {
        isRefreshing = false
      }
    }
    if (err.response && (err.response.status === 409 || err.response.status === 400)) {
        return Promise.reject(err);
    }

    logger.error(`axios.interceptor general error on ${originalReq.url}`, err);
    return Promise.reject(err)
  }
)

export default api