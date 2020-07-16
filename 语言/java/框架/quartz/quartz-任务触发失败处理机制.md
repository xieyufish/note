## 定时任务框架Quartz之任务触发失败(misfire)处理机制详解

### 一、任务触发失败原因

​	首先，quartz在执行定时任务过程中是采用多线程(默认是10个线程)机制来运行定时任务的，那么在我们的定时任务运行过程中，为什么会造成触发失败呢？这里总结如下几种情况：

1. 所有创建的线程资源全部出去繁忙状态，造成在某个定时任务时间到达时，无空闲的线程可用，从而可能造成触发失败；
2. 调度器被关闭或者是系统因为异常而崩溃，从而造成将要执行的定时任务没有被调度；
3. 上一次的调度执行还未结束，这种情况只有在被调度任务是ConcurrentExectionDisallowed(也就是大家说的有状态的任务)时才会发生。

### 二、任务触发失败识别

​	我们了解了任务触发失败的原因之后，那么Quartz又是怎么失败一个定时任务是否misfire呢？

​	这里涉及到Quartz中一个比较重要的配置：org.quartz.jobStore.misfireThreshold，单位是毫秒，这个配置是跟JobStore接口绑定的，在不同的JobStore实现中有不同的默认值；在RAMJobStore（Quartz默认配置的JobStore实现）中的初始值是5000毫秒，默认配置值是60000毫秒；这个值的作用是指定调度引擎设置触发器超时的"临界值"。也就是说Quartz对于任务的超时是有容忍度的，超过了这个容忍度才会判定为misfire。

​	假设org.quartz.jobStore.misfireThreshold=5000，现在有一个定时任务设定的时间是在10:30:30执行，只要是在10:30:35之前这个任务被调度，那么都可以触发成功，如果超过10:30:35这个时间还没有被调度执行，就意味这个任务触发失败，Quartz就会启动它的misfire处理机制，根据配置的失败处理机制决定是立刻重新执行、放弃执行还是等待下一次调度时执行。

### 三、任务触发失败处理方式

关键的点在于设置的各个策略是否修改了nextFireTime的值，从而产生不同的影响。

#### MISFIRE_INSTRUCTION_SMART_POLICY

“聪明策略”，根据具体的触发器实现类运行不同的触发失败处理机制。

##### a. SimpleTriggerImpl的“聪明策略“处理机制如下：

``````java
public void updateAfterMisfire(Calendar cal) {
    // 默认值为“聪明策略”：MISFIRE_INSTRUCTION_SMART_POLICY
    int instr = getMisfireInstruction();

    if(instr == Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY)
        return;
	
    // 1. 重新设置策略
    // 如果失败处理策略为“聪明策略”：MISFIRE_INSTRUCTION_SMART_POLICY
    // 每次调用都会触发这些判断
    if (instr == Trigger.MISFIRE_INSTRUCTION_SMART_POLICY) {
        // 判断repeatCount的值
        if (getRepeatCount() == 0) {
            // repeatCount=0，则将失败策略重新设置为“马上触发”：MISFIRE_INSTRUCTION_FIRE_NOW
            instr = MISFIRE_INSTRUCTION_FIRE_NOW;
        } else if (getRepeatCount() == REPEAT_INDEFINITELY) {
            // repeatCount=-1
            instr = MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT;
        } else {
            // repeatCount != 0 && repeatCount != -1
            instr = MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT;
        }
    } else if (instr == MISFIRE_INSTRUCTION_FIRE_NOW && getRepeatCount() != 0) {
        // 失败策略被用户手动设置为：MISFIRE_INSTRUCTION_FIRE_NOW
        // 从这里可以看出当我们手动将失败策略设置为MISFIRE_INSTRUCTION_FIRE_NOW时，
        // repeatCount值我们同时也要手动设置为非0值才会起作用。
        instr = MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_REMAINING_REPEAT_COUNT;
    }
	
    // 2. 继续判断策略值
    if (instr == MISFIRE_INSTRUCTION_FIRE_NOW) {
        // 用户设置的策略是MISFIRE_INSTRUCTION_FIRE_NOW，或者是“聪明策略”&&repeatCount=0
        // “马上触发”策略就是设置下一次激活时间为现在（new Date()）
        setNextFireTime(new Date());
    } else if (instr == MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_EXISTING_COUNT) {
        // 用户设置的策略是：MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_EXISTING_COUNT
        // “存在次数”策略意思就是获取现在开始下一次的激活时间newFireTime
        // 如果newFireTime不在触发器设置的日历中，就循环重复获取newFireTime之后的新的激活时间
        // 直到找到一个在日历的中激活时间或者newfireTime=null为止
        Date newFireTime = getFireTimeAfter(new Date());
        while (newFireTime != null && cal != null
               && !cal.isTimeIncluded(newFireTime.getTime())) {
            newFireTime = getFireTimeAfter(newFireTime);

            if(newFireTime == null)
                break;

            //avoid infinite loop
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTime(newFireTime);
            if (c.get(java.util.Calendar.YEAR) > YEAR_TO_GIVEUP_SCHEDULING_AT) {
                newFireTime = null;
            }
        }
        setNextFireTime(newFireTime);
    } else if (instr == MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT) {
        // 用户设置的策略是MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT
        // 或者是由“聪明策略”&&repeatCount=-1转化而来
        
        // 这段代码跟上面一样，也是不断的获取一个新的激活时间，直到是一个合格的激活时间就退出循环 
        Date newFireTime = getFireTimeAfter(new Date());
        while (newFireTime != null && cal != null
               && !cal.isTimeIncluded(newFireTime.getTime())) {
            newFireTime = getFireTimeAfter(newFireTime);

            if(newFireTime == null)
                break;

            //avoid infinite loop
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTime(newFireTime);
            if (c.get(java.util.Calendar.YEAR) > YEAR_TO_GIVEUP_SCHEDULING_AT) {
                newFireTime = null;
            }
        }
        
        // 额外的这里会设置一个被触发的次数值
        if (newFireTime != null) {
            int timesMissed = computeNumTimesFiredBetween(nextFireTime,
                                                          newFireTime);
            setTimesTriggered(getTimesTriggered() + timesMissed);
        }

        setNextFireTime(newFireTime);
    } else if (instr == MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT) {
        // 用户设置的策略是MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT
        // 或者由“聪明策略”&&repeatCount！=0&&repeatCount！=-1转化而来
        
        // 通过设置的repeatCount值来确定失败之后可以被激活的次数
        Date newFireTime = new Date();	// 马上触发
        if (repeatCount != 0 && repeatCount != REPEAT_INDEFINITELY) {
            setRepeatCount(getRepeatCount() - getTimesTriggered());
            setTimesTriggered(0);
        }
		
        // 如果触发时间已经超过了定时任务的最后一次定时时间，那么不再触发
        if (getEndTime() != null && getEndTime().before(newFireTime)) {
            setNextFireTime(null); // We are past the end time
        } else {
            setStartTime(newFireTime);
            setNextFireTime(newFireTime);
        } 
    } else if (instr == MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_REMAINING_REPEAT_COUNT) {
        // 用户设置的策略是MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_REMAINING_REPEAT_COUNT
        // 或者用户设置的策略是MISFIRE_INSTRUCTION_FIRE_NOW&&repeatCount！=0
        Date newFireTime = new Date();

        int timesMissed = computeNumTimesFiredBetween(nextFireTime,
                                                      newFireTime);

        if (repeatCount != 0 && repeatCount != REPEAT_INDEFINITELY) {
            int remainingCount = getRepeatCount()
                - (getTimesTriggered() + timesMissed);
            if (remainingCount <= 0) { 
                remainingCount = 0;
            }
            setRepeatCount(remainingCount);
            setTimesTriggered(0);
        }
		
        // 如果触发时间已经超过了定时任务的最后一次定时时间，那么不再触发
        if (getEndTime() != null && getEndTime().before(newFireTime)) {
            setNextFireTime(null); // We are past the end time
        } else {
            setStartTime(newFireTime);
            setNextFireTime(newFireTime);
        } 
    }

}
``````

##### b. CronTriggerImpl、CalendarIntervalTriggerImpl以及DailyTimeIntervalTriggerImpl的”聪明策略“处理机制是一样，如下：

``````java
public void updateAfterMisfire(org.quartz.Calendar cal) {
    // 默认值：MISFIRE_INSTRUCTION_SMART_POLICY
    int instr = getMisfireInstruction();

    if(instr == Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY)
        return;

    if (instr == MISFIRE_INSTRUCTION_SMART_POLICY) {
        // 如果是“聪明策略”那么就按MISFIRE_INSTRUCTION_FIRE_ONCE_NOW策略走
        instr = MISFIRE_INSTRUCTION_FIRE_ONCE_NOW;
    }

    if (instr == MISFIRE_INSTRUCTION_DO_NOTHING) {
        // 设置下一次合理激活时间
        Date newFireTime = getFireTimeAfter(new Date());
        while (newFireTime != null && cal != null
               && !cal.isTimeIncluded(newFireTime.getTime())) {
            newFireTime = getFireTimeAfter(newFireTime);
        }
        setNextFireTime(newFireTime);
    } else if (instr == MISFIRE_INSTRUCTION_FIRE_ONCE_NOW) {
        // 马上激活
        setNextFireTime(new Date());
    }
}
``````

#### MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY

属于Trigger接口中的常量，表示忽略超时状态，依旧按照特定触发器实现类的触发策略执行。
