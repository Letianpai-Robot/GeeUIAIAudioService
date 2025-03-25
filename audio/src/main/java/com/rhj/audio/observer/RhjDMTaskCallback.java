package com.rhj.audio.observer;

import com.rhj.audio.DmTaskResultBean;

public interface RhjDMTaskCallback {
    boolean dealResult(DmTaskResultBean dmTaskResultBean);

    void dealErrorResult();
}
