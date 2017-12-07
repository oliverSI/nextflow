package nextflow.executor

import java.nio.file.Files

import nextflow.processor.TaskBean
import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AwsBatchScriptLauncherTest extends Specification {

    def 'test bash wrapper with input'() {

        given:
        def folder = Files.createTempDirectory('test')

        /*
         * simple bash run
         */
        when:
        def opts = new AwsOptions(cliPath:'/conda/bin/aws', region: 'eu-west-1')
        def bash = new AwsBatchScriptLauncher([
                name: 'Hello 1',
                workDir: folder,
                script: 'echo Hello world!',
                environment: [FOO: 1, BAR:'any'],
                input: 'Ciao ciao'
        ] as TaskBean, opts)
        bash.build()

        then:
        Files.exists(folder.resolve('.command.sh'))
        Files.exists(folder.resolve('.command.run'))
        Files.exists(folder.resolve('.command.in'))

        folder.resolve('.command.in').text == 'Ciao ciao'

        folder.resolve('.command.sh').text ==
                '''
                #!/bin/bash -ue
                echo Hello world!
                '''
                        .stripIndent().leftTrim()


        folder.resolve('.command.run').text ==
                """
                #!/bin/bash
                # NEXTFLOW TASK: Hello 1
                set -e
                set -u
                NXF_DEBUG=\${NXF_DEBUG:=0}; [[ \$NXF_DEBUG > 1 ]] && set -x

                nxf_env() {
                    echo '============= task environment ============='
                    env | sort | sed "s/\\(.*\\)AWS\\(.*\\)=\\(.\\{6\\}\\).*/\\1AWS\\2=\\3xxxxxxxxxxxxx/"
                    echo '============= task output =================='
                }

                nxf_kill() {
                    declare -a ALL_CHILD
                    while read P PP;do
                        ALL_CHILD[\$PP]+=" \$P"
                    done < <(ps -e -o pid= -o ppid=)

                    walk() {
                        [[ \$1 != \$\$ ]] && kill \$1 2>/dev/null || true
                        for i in \${ALL_CHILD[\$1]:=}; do walk \$i; done
                    }

                    walk \$1
                }

                nxf_mktemp() {
                    local base=\${1:-/tmp}
                    if [[ \$(uname) = Darwin ]]; then mktemp -d \$base/nxf.XXXXXXXXXX
                    else TMPDIR="\$base" mktemp -d -t nxf.XXXXXXXXXX
                    fi
                }

                on_exit() {
                  exit_status=\${ret:=\$?}
                  printf \$exit_status | /conda/bin/aws --region eu-west-1 s3 cp - s3:/${folder}/.exitcode || true
                  set +u
                  [[ "\$COUT" ]] && rm -f "\$COUT" || true
                  [[ "\$CERR" ]] && rm -f "\$CERR" || true
                  rm -rf \$NXF_SCRATCH || true
                  exit \$exit_status
                }

                on_term() {
                    set +e
                    [[ "\$pid" ]] && nxf_kill \$pid
                }

                trap on_exit EXIT
                trap on_term TERM INT USR1 USR2

                NXF_SCRATCH="\$(set +u; nxf_mktemp \$TMPDIR)"
                [[ \$NXF_DEBUG > 0 ]] && nxf_env

                # aws helper
                nxf_s3_upload() {
                    local pattern=\$1
                    local s3path=\$2
                    for name in \$pattern;do
                      if [[ -d "\$name" ]]; then
                        /conda/bin/aws --region eu-west-1 s3 cp \$name \$s3path/\$name --quiet --recursive --storage-class STANDARD
                      else
                        /conda/bin/aws --region eu-west-1 s3 cp \$name \$s3path/\$name --quiet --storage-class STANDARD
                      fi
                  done
                }

                echo start | /conda/bin/aws --region eu-west-1 s3 cp - s3:/${folder}/.command.begin
                # task environment
                export FOO="1"
                export BAR="any"

                [[ \$NXF_SCRATCH ]] && echo "nxf-scratch-dir \$HOSTNAME:\$NXF_SCRATCH" && cd \$NXF_SCRATCH
                # stage input files
                rm -f .command.sh
                rm -f .command.in
                /conda/bin/aws --region eu-west-1 s3 cp --quiet s3:/${folder}/.command.sh .command.sh
                /conda/bin/aws --region eu-west-1 s3 cp --quiet s3:/${folder}/.command.in .command.in

                set +e
                COUT=\$PWD/.command.po; mkfifo "\$COUT"
                CERR=\$PWD/.command.pe; mkfifo "\$CERR"
                tee .command.out < "\$COUT" &
                tee1=\$!
                tee .command.err < "\$CERR" >&2 &
                tee2=\$!
                (
                /bin/bash -ue .command.sh < .command.in
                ) >"\$COUT" 2>"\$CERR" &
                pid=\$!
                wait \$pid || ret=\$?
                wait \$tee1 \$tee2
                nxf_s3_upload .command.out s3:/${folder} || true
                nxf_s3_upload .command.err s3:/${folder} || true
                """
                        .stripIndent().leftTrim()


        cleanup:
        folder?.deleteDir()
    }


    def 'test bash wrapper with outputs and stats'() {

        given:
        def folder = Files.createTempDirectory('test')

        /*
         * simple bash run
         */
        when:
        def opts = new AwsOptions()
        def bash = new AwsBatchScriptLauncher([
                name: 'Hello 1',
                workDir: folder,
                targetDir: folder,
                statsEnabled: true,
                outputFiles: ['foo.txt', 'bar.fastq'],
                script: 'echo Hello world!',
                input: 'Ciao ciao'
        ] as TaskBean, opts)
        bash.build()

        then:
        Files.exists(folder.resolve('.command.sh'))
        Files.exists(folder.resolve('.command.run'))
        Files.exists(folder.resolve('.command.in'))

        folder.resolve('.command.in').text == 'Ciao ciao'

        folder.resolve('.command.sh').text ==
                '''
                #!/bin/bash -ue
                echo Hello world!
                '''
                        .stripIndent().leftTrim()


        folder.resolve('.command.run').text ==
                """
                #!/bin/bash
                # NEXTFLOW TASK: Hello 1
                set -e
                set -u
                NXF_DEBUG=\${NXF_DEBUG:=0}; [[ \$NXF_DEBUG > 1 ]] && set -x

                nxf_env() {
                    echo '============= task environment ============='
                    env | sort | sed "s/\\(.*\\)AWS\\(.*\\)=\\(.\\{6\\}\\).*/\\1AWS\\2=\\3xxxxxxxxxxxxx/"
                    echo '============= task output =================='
                }

                nxf_kill() {
                    declare -a ALL_CHILD
                    while read P PP;do
                        ALL_CHILD[\$PP]+=" \$P"
                    done < <(ps -e -o pid= -o ppid=)

                    walk() {
                        [[ \$1 != \$\$ ]] && kill \$1 2>/dev/null || true
                        for i in \${ALL_CHILD[\$1]:=}; do walk \$i; done
                    }

                    walk \$1
                }

                nxf_mktemp() {
                    local base=\${1:-/tmp}
                    if [[ \$(uname) = Darwin ]]; then mktemp -d \$base/nxf.XXXXXXXXXX
                    else TMPDIR="\$base" mktemp -d -t nxf.XXXXXXXXXX
                    fi
                }

                on_exit() {
                  exit_status=\${ret:=\$?}
                  printf \$exit_status | aws s3 cp - s3:/${folder}/.exitcode || true
                  set +u
                  [[ "\$COUT" ]] && rm -f "\$COUT" || true
                  [[ "\$CERR" ]] && rm -f "\$CERR" || true
                  rm -rf \$NXF_SCRATCH || true
                  exit \$exit_status
                }

                on_term() {
                    set +e
                    [[ "\$pid" ]] && nxf_kill \$pid
                }

                trap on_exit EXIT
                trap on_term TERM INT USR1 USR2

                NXF_SCRATCH="\$(set +u; nxf_mktemp \$TMPDIR)"
                [[ \$NXF_DEBUG > 0 ]] && nxf_env

                # aws helper
                nxf_s3_upload() {
                    local pattern=\$1
                    local s3path=\$2
                    for name in \$pattern;do
                      if [[ -d "\$name" ]]; then
                        aws s3 cp \$name \$s3path/\$name --quiet --recursive --storage-class STANDARD
                      else
                        aws s3 cp \$name \$s3path/\$name --quiet --storage-class STANDARD
                      fi
                  done
                }

                echo start | aws s3 cp - s3:/${folder}/.command.begin
                [[ \$NXF_SCRATCH ]] && echo "nxf-scratch-dir \$HOSTNAME:\$NXF_SCRATCH" && cd \$NXF_SCRATCH
                # stage input files
                rm -f .command.sh
                rm -f .command.stub
                rm -f .command.in
                aws s3 cp --quiet s3:/${folder}/.command.sh .command.sh
                aws s3 cp --quiet s3:/${folder}/.command.stub .command.stub
                aws s3 cp --quiet s3:/${folder}/.command.in .command.in

                set +e
                COUT=\$PWD/.command.po; mkfifo "\$COUT"
                CERR=\$PWD/.command.pe; mkfifo "\$CERR"
                tee .command.out < "\$COUT" &
                tee1=\$!
                tee .command.err < "\$CERR" >&2 &
                tee2=\$!
                (
                /bin/bash .command.stub
                ) >"\$COUT" 2>"\$CERR" &
                pid=\$!
                wait \$pid || ret=\$?
                wait \$tee1 \$tee2
                nxf_s3_upload .command.out s3:/${folder} || true
                nxf_s3_upload .command.err s3:/${folder} || true
                # copies output files to target
                if [[ \${ret:=0} == 0 ]]; then
                  nxf_s3_upload 'foo.txt' s3:/${folder} || true
                  nxf_s3_upload 'bar.fastq' s3:/${folder} || true
                fi
                nxf_s3_upload .command.trace s3:/${folder} || true
                """
                        .stripIndent().leftTrim()


        cleanup:
        folder?.deleteDir()
    }
}
