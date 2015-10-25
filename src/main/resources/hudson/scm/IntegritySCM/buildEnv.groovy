package hudson.scm.IntegritySCM;

def l = namespace(lib.JenkinsTagLib)

["MKSSI_PROJECT","MKSSI_HOST","MKSSI_PORT","MKSSI_USER","MKSSI_BUILD"].each { name ->
    l.buildEnvVar(name:name) {
        raw(_("${name}.blurb"))
    }
}