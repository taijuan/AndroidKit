package com.taijuan.retrofit

import androidx.lifecycle.LiveData
import retrofit2.*
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 错误返回
 */
fun <T> error(error: String) = Error<T>(error)

/**
 * 成功返回
 */
fun <T> success(response: Response<T>): SuccessError<T> =
    if (response.isSuccessful) {
        val body = response.body()
        if (body == null) {
            Error("body is empty")
        } else {
            Success(body = body)
        }
    } else {
        Error(response.message() ?: "unknown errorMsg")
    }

/**
 *成功错误返回基类
 */
sealed class SuccessError<T>

/**
 * 成功对象
 */
data class Success<T>(val body: T) : SuccessError<T>()

/**
 * 错误对象
 */
data class Error<T>(val errorMsg: String) : SuccessError<T>()

/**
 * 集成至CallAdapter
 * 主要功能数据转换成LiveData<SuccessError<R>>
 */
class LiveDataCallAdapter<R>(private val responseType: Type) :
    CallAdapter<R, LiveData<SuccessError<R>>> {
    override fun responseType() = responseType
    override fun adapt(call: Call<R>): LiveData<SuccessError<R>> {
        return object : LiveData<SuccessError<R>>() {
            private var started = AtomicBoolean(false)
            override fun onActive() {
                super.onActive()
                if (started.compareAndSet(false, true)) {
                    call.enqueue(object : Callback<R> {
                        override fun onResponse(call: Call<R>, response: Response<R>) {
                            postValue(success(response))
                        }

                        override fun onFailure(call: Call<R>, throwable: Throwable) {
                            postValue(error(throwable.toString()))
                        }
                    })
                }
            }
        }
    }
}


/**
 * CallAdapter.Factory工厂
 */
class LiveDataCallAdapterFactory : CallAdapter.Factory() {

    companion object {
        @JvmOverloads
        fun create(): LiveDataCallAdapterFactory {
            return LiveDataCallAdapterFactory()
        }
    }

    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        require(getRawType(returnType) == LiveData::class.java) { "returnType  must  be LiveData" }
        val observableType = getParameterUpperBound(0, returnType as ParameterizedType)
        val rawObservableType = getRawType(observableType)
        require(rawObservableType == SuccessError::class.java) { "type must be a SuccessError" }
        require(observableType is ParameterizedType) { "resource must be parameterized" }
        val bodyType = getParameterUpperBound(0, observableType)
        return LiveDataCallAdapter<Any>(bodyType)
    }
}
