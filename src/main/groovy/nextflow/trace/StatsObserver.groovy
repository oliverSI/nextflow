package nextflow.trace

import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.processor.TaskProcessor
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class StatsObserver implements TraceObserver {


    private WorkflowStats stats = new WorkflowStats()


    protected long getCpuTime( TraceRecord record ) {
        final time = (long)record.get('realtime')
        final cpus = (long)record.get('cpus')
        return time * cpus / 1000 as long
    }


    @Override
    void onFlowStart(Session session) {

    }

    @Override
    void onFlowComplete() {

    }

    @Override
    void onProcessCreate(TaskProcessor process) {

    }

    @Override
    void onProcessSubmit(TaskHandler handler) {

    }

    @Override
    void onProcessStart(TaskHandler handler) {

    }

    @Override
    void onProcessComplete(TaskHandler handler) {
        final record = handler.getTraceRecord()
        synchronized (stats) {
            if( record.get('status') == 'COMPLETED' ) {
                stats.completed++
                stats.timeSucceed += getCpuTime(record)
            }
            else {
                stats.timeFailed += getCpuTime(record)
            }
        }
    }

    @Override
    void onProcessCached(TaskHandler handler) {
        final record = handler.getTraceRecord()
        synchronized (stats) {
            stats.timeCached += getCpuTime(record)
            stats.cached
        }
    }
}
