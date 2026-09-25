import '.just/format/Justfile'
import '.just/lint/Justfile'

mod build '.just/build'
mod test '.just/test'
mod start '.just/start'

[private]
default:
    just --list --list-submodules

[private]
ui-deps:
    [ -d ui/node_modules ] || npm --prefix ui ci
