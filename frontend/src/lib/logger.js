class Logger {
  info(message, ...args) {
    console.log(message, ...args)
  }

  warn(message, ...args) {
    console.warn(message, ...args)
  }

  error(context, error) {
    const errorMessage = `[${context}]: ${error.message}`
    console.error(errorMessage, {
      context,
      errorObject: error,
      stack: error.stack,
      response: error.response?.data,
    })
  }
}

export const logger = new Logger()