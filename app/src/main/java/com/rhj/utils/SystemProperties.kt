package com.rhj.utils

 object SystemProperties{
    private val clazz = Class.forName("android.os.SystemProperties")

    fun set(key:String,value:String){
        val method = clazz.getMethod("set", String::class.java, String::class.java)
        method.invoke(clazz,key,value)
    }

    fun get(key:String):String?{
        val method = clazz.getMethod("get",String::class.java)
        return method.invoke(clazz,key) as? String
    }

    fun get(key:String,default:String):String?{
        val method = clazz.getMethod("get",String::class.java,String::class.java)
        return method.invoke(clazz,key,default) as? String
    }

    fun getLong(key:String,def:Long): Long?{
        val method = clazz.getMethod("getLong",String::class.java,Long::class.java)
        return method.invoke(clazz,key,def) as? Long
    }

    fun getInt(key:String,def:Int): Int?{
        val method = clazz.getMethod("getInt",String::class.java,Int::class.java)
        return method.invoke(clazz,key,def) as? Int
    }

    fun getBoolean(key:String,def:Boolean): Boolean?{
        val method = clazz.getMethod("getBoolean",String::class.java,Boolean::class.java)
        return method.invoke(clazz,key,def) as? Boolean
    }
}