package nextflow.trace

import java.text.DecimalFormat

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class WorkflowStats {

    /**
     * Overall workflow compute time as CPUs-seconds for task executed successfully
     */
    long timeSucceed

    /**
     * Overall compute time for cached tasks
     */
    long timeCached

    /**
     * Overall compute time for failed tasks
     */
    long timeFailed

    long countSuccess

    long completed

    long cached

    long countFailed

    long countIgnored

    /**
     * @return A formatted string representing the overall execution time as CPU-Hours
     */
    protected String getComputeTime() {

        def fmt = new DecimalFormat("0.#")

        def total = (timeSucceed + timeCached + timeFailed)
        def result = String.format('%.1f', total/3600)
        if( timeCached || timeFailed ) {
            result += ' ('
            def items = []
            if( timeCached )
                items << fmt.format(timeCached/total*100) + '% cached'
            if( timeFailed )
                items << fmt.format(timeFailed/total*100) + '% failed'
            result += items.join(', ')
            result += ')'
        }

        return result
    }
}
