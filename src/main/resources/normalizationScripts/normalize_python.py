import sys
import libcst as cst

class SortMembersTransformer(cst.CSTTransformer):
    def leave_ClassDef(self, original_node, updated_node):
        body = updated_node.body
        methods = [n for n in body.body if isinstance(n, cst.FunctionDef)]
        others = [n for n in body.body if not isinstance(n, cst.FunctionDef)]
        
        sorted_methods = sorted(methods, key=lambda n: n.name.value)
        
        new_body = body.with_changes(body=[*others, *sorted_methods])
        return updated_node.with_changes(body=new_body)

def main():
    if len(sys.argv) < 2:
        print("Uso: python normalize_python.py <caminho_do_arquivo>")
        sys.exit(1)

    file_path = sys.argv[1]

    try:
        with open(file_path, "r", encoding="utf-8") as f:
            source_code = f.read()

        tree = cst.parse_module(source_code)

        transformer = SortMembersTransformer()
        modified_tree = tree.visit(transformer)

        print(modified_tree.code)

    except Exception as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()