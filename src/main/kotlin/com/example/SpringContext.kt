package com.example

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class SpringContext : ApplicationContextAware {
    override fun setApplicationContext(ctx: ApplicationContext) {
        context = ctx
    }

    companion object {
        @Volatile
        private var context: ApplicationContext? = null

        @JvmStatic
        fun <T> getBean(beanClass: Class<T>): T {
            val ctx = context
            requireNotNull(ctx) { "Spring ApplicationContext is not initialized yet" }
            return ctx.getBean(beanClass)
        }
    }
}
