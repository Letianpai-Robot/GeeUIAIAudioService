/*==============================================================================
FMOD Example Framework
Copyright (c), Firelight Technologies Pty, Ltd 2013-2023.
==============================================================================*/
#include "common.h"
#include <string.h>
#include <jni.h>
#include <unistd.h>
#include <string>
#include <vector>

JNIEnv *gJNIEnv;
jobject gMainActivityObject;
int gDownButtons;
int gLastDownButtons;
int gPressedButtons;
bool gSuspendState;
bool gQuitState;
std::string gUIString;
std::vector<char *> gPathList;

int FMOD_Main(const char* url); // Defined in example
int FMOD_play(const char *url);

void Common_Init(void **extraDriverData)
{
	gDownButtons = 0;
	gLastDownButtons = 0;
	gPressedButtons = 0;
	gSuspendState = false;
	gQuitState = false;
}

void Common_Close()
{
    for (std::vector<char *>::iterator item = gPathList.begin(); item != gPathList.end(); ++item)
    {
        free(*item);
    }

    gPathList.clear();
}

void Common_Update()
{
	jstring text = gJNIEnv->NewStringUTF(gUIString.c_str());
	jclass mainActivityClass = gJNIEnv->GetObjectClass(gMainActivityObject);
    jmethodID updateScreenMethodID = gJNIEnv->GetMethodID(mainActivityClass, "updateScreen", "(Ljava/lang/String;)V");

    gJNIEnv->CallVoidMethod(gMainActivityObject, updateScreenMethodID, text);

    gJNIEnv->DeleteLocalRef(text);
    gJNIEnv->DeleteLocalRef(mainActivityClass);

    gUIString.clear();

    gPressedButtons = (gLastDownButtons ^ gDownButtons) & gDownButtons;
    gLastDownButtons = gDownButtons;

    if (gQuitState)
    {
    	gPressedButtons |= (1 << BTN_QUIT);
    }
}

void Common_Exit(int returnCode)
{
    exit(returnCode);
}

void Common_DrawText(const char *text)
{
    char s[256];
    snprintf(s, sizeof(s), "%s\n", text);
    
    gUIString.append(s);
}

bool Common_BtnPress(Common_Button btn)
{
    return ((gPressedButtons & (1 << btn)) != 0);
}

bool Common_BtnDown(Common_Button btn)
{
    return ((gDownButtons & (1 << btn)) != 0);
}

const char *Common_BtnStr(Common_Button btn)
{
    switch (btn)
    {
        case BTN_ACTION1: return "A";
        case BTN_ACTION2: return "B";
        case BTN_ACTION3: return "C";
        case BTN_ACTION4: return "D";
        case BTN_UP:      return "Up";
        case BTN_DOWN:    return "Down";
        case BTN_LEFT:    return "Left";
        case BTN_RIGHT:   return "Right";
        case BTN_MORE:    return "E";
        case BTN_QUIT:    return "Back";
        default:          return "Unknown";
    }
}

const char *Common_MediaPath(const char *fileName)
{
    char *filePath = (char *)calloc(256, sizeof(char));

    strcat(filePath, "file:///android_asset/");
    strcat(filePath, fileName);
    gPathList.push_back(filePath);

    return filePath;
}

const char *Common_WritePath(const char *fileName)
{
	return Common_MediaPath(fileName);
}

bool Common_SuspendState()
{
	return gSuspendState;
}

extern "C"
{

jstring Java_org_fmod_example_MainActivity_getButtonLabel(JNIEnv *env, jobject thiz, jint index)
{
    return env->NewStringUTF(Common_BtnStr((Common_Button)index));
}

void Java_org_fmod_example_MainActivity_buttonDown(JNIEnv *env, jobject thiz, jint index)
{
    gDownButtons |= (1 << index);
}

void Java_org_fmod_example_MainActivity_buttonUp(JNIEnv *env, jobject thiz, jint index)
{
    gDownButtons &= ~(1 << index);
}

void Java_org_fmod_example_MainActivity_setStateCreate(JNIEnv *env, jobject thiz)
{

}

void Java_org_fmod_example_MainActivity_setStateStart(JNIEnv *env, jobject thiz)
{
	gSuspendState = false;
}

void Java_org_fmod_example_MainActivity_setStateStop(JNIEnv *env, jobject thiz)
{
	gSuspendState = true;
}

void Java_org_fmod_example_MainActivity_setStateDestroy(JNIEnv *env, jobject thiz)
{
	gQuitState = true;
}

void Java_org_fmod_example_MainActivity_main(JNIEnv *env, jobject thiz)
{
	gJNIEnv = env;
	gMainActivityObject = thiz;

//	FMOD_Main("");
}

} /* extern "C" */

jobject j_callback_obj;
jmethodID j_callback_finish_listener;
jmethodID j_callback_error_listener;

//static JNINativeMethod gMethods[] = {
//        {"registerSensorDataListener", "(Lcom/letianpai/sensorservice/MCUSensorListener;)V",
//         (void *) register_listener},
//};

// 将 Java 对象转为全局引用，以便在 C++ 层中持有该对象
void set_callback_obj(JNIEnv *env, jobject obj) {
    if (j_callback_obj != nullptr) {
        env->DeleteGlobalRef(j_callback_obj);
    }
    j_callback_obj = env->NewGlobalRef(obj);
}

void callJavaCallback(JNIEnv* env, jobject javaObject) {
    jclass javaClass = env->GetObjectClass(javaObject);
    jmethodID methodID = env->GetMethodID(javaClass, "onFinish", "()V");
    env->CallVoidMethod(javaObject, methodID);
}

jobject jobject1;
void callBackSound(){
    callJavaCallback(gJNIEnv, jobject1);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_demon_fmodsound_FmodSound_playTts(JNIEnv *env, jobject thiz, jstring url,jobject listener) {
    gJNIEnv = env;
    gMainActivityObject = thiz;
    jobject1 = listener;
    const char *urlc = env->GetStringUTFChars(url, NULL);
    FMOD_play(urlc);
    env->ReleaseStringUTFChars(url, urlc);
}