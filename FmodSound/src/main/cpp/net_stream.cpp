/*==============================================================================
Net Stream Example
Copyright (c), Firelight Technologies Pty, Ltd 2004-2023.

This example shows how to play streaming audio from an Internet source

For information on using FMOD example code in your own programs, visit
https://www.fmod.com/legal
==============================================================================*/
#include "inc/fmod.hpp"
#include "common.h"
#include <unistd.h>

int isComplete = 0;
FMOD_RESULT F_CALLBACK myCompletionCallback(FMOD_CHANNELCONTROL* channelControl, FMOD_CHANNELCONTROL_TYPE controlType, FMOD_CHANNELCONTROL_CALLBACK_TYPE callbackType, void* commanddata1, void* commanddata2)
{
    isComplete = 1;
    // 在这里执行播放完成后的操作
    // 可以在这里触发其他事件或处理逻辑
    callBackSound();
    return FMOD_OK;
}


FMOD::Channel  *channel = 0;
int FMOD_play(const char *url){
    channel->stop();
    //正在播放
    bool playing = true;
    FMOD::System    *system = 0;
    FMOD::Sound     *sound = 0;
    FMOD_RESULT result = FMOD::System_Create(&system);
    if (result != FMOD_OK){
        LOGD("System_Create ERROR");
    }
    FMOD_RESULT initResult = system->init(32, FMOD_INIT_NORMAL, nullptr);
    if (initResult != FMOD_OK) {
        // 处理初始化错误
        LOGD("System_init ERROR");
    }

    FMOD_CREATESOUNDEXINFO exinfo;
    memset(&exinfo, 0, sizeof(FMOD_CREATESOUNDEXINFO));
    exinfo.cbsize = sizeof(FMOD_CREATESOUNDEXINFO);
    exinfo.filebuffersize = 1024*64;

    FMOD_RESULT urlResult = system->createStream(url, FMOD_DEFAULT, NULL, &sound);
    if (urlResult != FMOD_OK) {
        // 处理创建音频资源错误
        LOGD("create stream ERROR");
    }

    FMOD_RESULT playResult = system->playSound(sound, nullptr, false, &channel);
    if (playResult != FMOD_OK) {
        // 处理播放错误
        LOGD("playResult ERROR");
    }

    // 播放声音...
    channel->setCallback(myCompletionCallback);
    FMOD::DSP *dsp_1;
    FMOD::DSP *dsp;

    system->createDSPByType(FMOD_DSP_TYPE_PITCHSHIFT, &dsp);
    dsp->setParameterFloat(FMOD_DSP_PITCHSHIFT_PITCH, 1.63);
    dsp->setParameterFloat(FMOD_DSP_PITCHSHIFT_OVERLAP, 32);
    dsp->setParameterFloat(FMOD_DSP_PITCHSHIFT_FFTSIZE, 256);

    system->createDSPByType(FMOD_DSP_TYPE_FADER, &dsp_1);
    dsp_1->setParameterFloat(FMOD_DSP_FADER_GAIN, 10);

    channel->addDSP(0, dsp);
    channel->addDSP(0, dsp_1);

    system->update();
    while (playing) {
        channel->isPlaying(&playing);
        usleep(1000);
        // 进行其他的游戏逻辑或处理
    }
    sound->release();
    system->close();
    system->release();
}

int FMOD_Main(const char *url)
{
    FMOD::System    *system = 0;
    FMOD::Sound     *sound = 0;
    FMOD::Channel   *channel = 0;
    FMOD_RESULT      result = FMOD_OK;
    FMOD_OPENSTATE   openstate = FMOD_OPENSTATE_READY;
    void            *extradriverdata = 0;
    const int        tagcount = 4;
    int              tagindex = 0;
    char             tagstring[tagcount][128] = { };
    
    Common_Init(&extradriverdata);
    
    /*
        Create a System object and initialize.
    */
    result = FMOD::System_Create(&system);
    ERRCHECK(result);

    result = system->init(1, FMOD_INIT_NORMAL, extradriverdata);
    ERRCHECK(result);

    /* Increase the file buffer size a little bit to account for Internet lag. */
    result = system->setStreamBufferSize(64*1024, FMOD_TIMEUNIT_RAWBYTES);
    ERRCHECK(result);

    FMOD_CREATESOUNDEXINFO exinfo;
    memset(&exinfo, 0, sizeof(FMOD_CREATESOUNDEXINFO));
    exinfo.cbsize = sizeof(FMOD_CREATESOUNDEXINFO);
    exinfo.filebuffersize = 1024*16;        /* Increase the default file chunk size to handle seeking inside large playlist files that may be over 2kb. */

//    result = system->createSound("http://icecast.omroep.nl/radio1-bb-mp3", FMOD_CREATESTREAM | FMOD_NONBLOCKING, &exinfo, &sound);
    result = system->createSound(url, FMOD_CREATESTREAM | FMOD_NONBLOCKING, &exinfo, &sound);
//    result = system->createSound("https://dds-ack.dui.ai/runtime/v1/longtext/86ff3a5c0a6a4b92a4f26ffaa9c85a41:7ce0f360a9394fe0b484ee45d34f988e?productId=279614681&aispeech-da-env=hd-ack", FMOD_CREATESTREAM | FMOD_NONBLOCKING, &exinfo, &sound);

    ERRCHECK(result);

    /*
        Main loop
    */
    do
    {
        unsigned int    pos = 0;
        unsigned int    percent = 0;
        bool            playing = false;
        bool            paused = false;
        bool            starving = false;
        const char     *state = "Stopped";
        
//        Common_Update();

//        if (Common_BtnPress(BTN_ACTION1))
//        {
//            if (channel)
//            {
//                bool paused = false;
//
//                result = channel->getPaused(&paused);
//                ERRCHECK(result);
//                result = channel->setPaused(!paused);
//                ERRCHECK(result);
//            }
//        }
        
        result = system->update();
        ERRCHECK(result);
        
        result = sound->getOpenState(&openstate, &percent, &starving, 0);
        ERRCHECK(result);
        
        {
            FMOD_TAG tag;
        
            /*
                Read any tags that have arrived, this could happen if a radio station switches
                to a new song.
            */
            while (sound->getTag(0, -1, &tag) == FMOD_OK)
            {
                if (tag.datatype == FMOD_TAGDATATYPE_STRING)
                {
                    snprintf(tagstring[tagindex], 128, "%s = '%s' (%d bytes)", tag.name, (char *)tag.data, tag.datalen);
                    tagindex = (tagindex + 1) % tagcount;

                    if (tag.type == FMOD_TAGTYPE_PLAYLIST && !strcmp(tag.name, "FILE"))
                    {
                        char url[256] = {};

                        strncpy(url, (const char *)tag.data, 255);  /* data point to sound owned memory, copy it before the sound is released. */

                        result = sound->release();
                        ERRCHECK(result);

                        result = system->createSound(url, FMOD_CREATESTREAM | FMOD_NONBLOCKING, &exinfo, &sound);    
                        ERRCHECK(result);
                    }

                }
                else if (tag.type == FMOD_TAGTYPE_FMOD)
                {
                    /* When a song changes, the sample rate may also change, so compensate here. */
                    if (!strcmp(tag.name, "Sample Rate Change") && channel)
                    {
                        float frequency = *((float *)tag.data);
                        
                        result = channel->setFrequency(frequency);
                        ERRCHECK(result);
                    }
                }
            }
        }
            
        if (channel)
        {
            result = channel->getPaused(&paused);
            ERRCHECK(result);
            
            result = channel->isPlaying(&playing);
            ERRCHECK(result);
            
            result = channel->getPosition(&pos, FMOD_TIMEUNIT_MS);
            ERRCHECK(result);
            
            /* Silence the stream until we have sufficient data for smooth playback. */
            result = channel->setMute(starving);
            ERRCHECK(result);
        }
        else
        {
             /* This may fail if the stream isn't ready yet, so don't check the error code. */
            system->playSound(sound, 0, false, &channel);
            FMOD::DSP *dsp_1;
            FMOD::DSP *dsp;

            system->createDSPByType(FMOD_DSP_TYPE_PITCHSHIFT, &dsp);
            dsp->setParameterFloat(FMOD_DSP_PITCHSHIFT_PITCH, 1.63);
            dsp->setParameterFloat(FMOD_DSP_PITCHSHIFT_OVERLAP, 32);
            dsp->setParameterFloat(FMOD_DSP_PITCHSHIFT_FFTSIZE, 256);

            system->createDSPByType(FMOD_DSP_TYPE_FADER, &dsp_1);
            dsp_1->setParameterFloat(FMOD_DSP_FADER_GAIN, 10);

            channel->addDSP(0, dsp);
            channel->addDSP(0, dsp_1);

            system->update();
        }
        
        if (openstate == FMOD_OPENSTATE_BUFFERING)
        {
            state = "Buffering...";
        }
        else if (openstate == FMOD_OPENSTATE_CONNECTING)
        {
            state = "Connecting...";
        }
        else if (paused)
        {
            state = "Paused";
        }
        else if (playing)
        {
            state = "Playing";
        }

        Common_Draw("==================================================");
        Common_Draw("Net Stream Example.");
        Common_Draw("Copyright (c) Firelight Technologies 2004-2023.");
        Common_Draw("==================================================");
        Common_Draw("");
        Common_Draw("Press %s to toggle pause", Common_BtnStr(BTN_ACTION1));
        Common_Draw("Press %s to quit", Common_BtnStr(BTN_QUIT));
        Common_Draw("");
        Common_Draw("Time = %02d:%02d:%02d", pos / 1000 / 60, pos / 1000 % 60, pos / 10 % 100);
        Common_Draw("State = %s %s", state, starving ? "(STARVING)" : "");
        Common_Draw("Buffer Percentage = %d", percent);
        Common_Draw("");
        Common_Draw("Tags:");
        for (int i = tagindex; i < (tagindex + tagcount); i++)
        {
            Common_Draw("%s", tagstring[i % tagcount]);
            Common_Draw("");
        }

        Common_Sleep(50);
    } while (!Common_BtnPress(BTN_QUIT));

    /*
        Stop the channel, then wait for it to finish opening before we release it.
    */    
    if (channel)
    {
        result = channel->stop();
        ERRCHECK(result);
    }

    do
    {
//        Common_Update();
        Common_Draw("Waiting for sound to finish opening before trying to release it....", Common_BtnStr(BTN_ACTION1));
        Common_Sleep(50);
        
        result = system->update();
        ERRCHECK(result);

        result = sound->getOpenState(&openstate, 0, 0, 0);
        ERRCHECK(result);
    } while (openstate != FMOD_OPENSTATE_READY);

    /*
        Shut down
    */
    result = sound->release();
    ERRCHECK(result);
    result = system->close();
    ERRCHECK(result);
    result = system->release();
    ERRCHECK(result);

    Common_Close();
    
    return 0;
}
