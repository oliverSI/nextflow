package nextflow.extension

import groovy.util.logging.Slf4j
import groovyx.gpars.dataflow.operator.DataflowProcessor
import nextflow.splitter.FastqSplitter
/**
 * Implements the inner merging logic for splitterXxx operator(s)
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class SplitterMergeClosure extends Closure {

    private int numOfParams

    private List<Integer> indexes

    private int emissionCount

    SplitterMergeClosure(List<Integer> indexes) {
        super(null, null);
        this.numOfParams = indexes.size()
        this.indexes = indexes
    }

    @Override
    public int getMaximumNumberOfParameters() {
        numOfParams
    }

    @Override
    public Class[] getParameterTypes() {
        Collections.nCopies(numOfParams, Object)
    }

    @Override
    public void setDelegate(final Object delegate) {
        super.setDelegate(delegate);
    }

    @Override
    public void setResolveStrategy(final int resolveStrategy) {
        super.setResolveStrategy(resolveStrategy);
    }

    @Override
    public Object call(final Object arguments) {
        throw new UnsupportedOperationException()
    }

    @Override
    public Object call(final Object... args) {
        log.trace "merging ($emissionCount) indexes=$indexes; args=$args"
        List result = null
        boolean header = false
        for( int i=0; i<args.size(); i++ ) {
            def item = args[i]
            // - When Pair-ended splitting is enabled the Fastq splitter
            //   emits the indexes of the splitted files as first tuple
            // - Those indexes are needed to merge the final tuple in correct order
            if( emissionCount==0 && item instanceof FastqSplitter.SplitIndex ) {
                indexes[i] = item.value
                header |= true
            }

            // - Compose the i-th tuple
            else if( item instanceof List ) {
                List entry = item
                if( i==0 ) {
                    result = new ArrayList(entry)
                }
                result[indexes[i]] = entry[indexes[i]]
            }
            else
                throw new IllegalArgumentException("Invalid splitter entry -- offending value=$item; indexes=$indexes; args=$args")

        }

        // emit the merged tuple (skipping the header)
        def processor = (DataflowProcessor)getDelegate()
        if( !header && processor ) {
            log.trace "merging ($emissionCount) result=$result"
            processor.bindAllOutputsAtomically(result);
        }

        emissionCount++

        return result;
    }

    @Override
    public Object call() {
        throw new UnsupportedOperationException()
    }
}