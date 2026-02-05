const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;
const t = require('@babel/types');

const filePath = process.argv[2];

if (!filePath) {
    console.error("Uso: node normalize_js.js <caminho_do_arquivo>");
    process.exit(1);
}

try {
    const code = fs.readFileSync(filePath, 'utf8');

    const ast = parser.parse(code, {
        sourceType: 'module',
        plugins: ['typescript', 'jsx', 'classProperties', 'decorators-legacy', 'dynamicImport']
    });

    traverse(ast, {
        ClassBody(path) {
            const members = path.node.body;
            
            const others = members.filter(member => 
                !t.isClassMethod(member) || member.kind === 'constructor'
            );

            const methods = members.filter(member => 
                t.isClassMethod(member) && member.kind !== 'constructor'
            );

            methods.sort((a, b) => {
                const nameA = a.key.name || a.key.value || "";
                const nameB = b.key.name || b.key.value || "";
                return nameA.localeCompare(nameB);
            });

            path.node.body = [...others, ...methods];
        }
    });

    const output = generate(ast, {
        retainLines: true,
        compact: false,
        comments: true
    }, code);
    
    console.log(output.code);

} catch (e) {
    console.error(e.message);
    process.exit(1);
}