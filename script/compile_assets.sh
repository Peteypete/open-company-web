#!/bin/bash

cd $2/target/public/ && \
java -jar ~/closure_compiler/closure-compiler-v$1.jar \
-W QUIET \
--js_output_file oc_assets.js \
--create_source_map oc_assets.js.map \
--js lib/print_ascii.js \
--js lib/truncate/jquery.truncate.js \
--js lib/scrollTo/scrollTo.min.js \
--js js/emojione/autocomplete.js \
--js lib/js-utils/svg-utils.js \
--js lib/js-utils/pasteHtmlAtCaret.js \
--js lib/cleanHTML/cleanHTML.js \
--js lib/MediumEditorAutolist/autolist.js \
--js lib/select2/js/select2.js \
--output_wrapper "%output%
//# sourceMappingURL=$3/oc_assets.js.map"

cd $2/target/public/ && \
cat <<EOT >> oc.js

//# sourceMappingURL=$3/oc.js.map"
EOT