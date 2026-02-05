package main

import (
	"fmt"
	"go/ast"
	"go/parser"
	"go/printer"
	"go/token"
	"os"
	"sort"
	"strings"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "Uso: go run normalize_go.go <arquivo>")
		os.Exit(1)
	}

	filePath := os.Args[1]
	fset := token.NewFileSet()

	node, err := parser.ParseFile(fset, filePath, nil, parser.ParseComments)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	var others []ast.Decl
	var functions []*ast.FuncDecl
	var inits []*ast.FuncDecl

	for _, decl := range node.Decls {
		if fn, ok := decl.(*ast.FuncDecl); ok {
			if fn.Name.Name == "init" {
				inits = append(inits, fn)
			} else {
				functions = append(functions, fn)
			}
		} else {
			others = append(others, decl)
		}
	}

	sort.Slice(functions, func(i, j int) bool {
		nameA := functions[i].Name.Name
		nameB := functions[j].Name.Name
		
		if functions[i].Recv != nil && len(functions[i].Recv.List) > 0 {
			typeA := fmt.Sprint(functions[i].Recv.List[0].Type)
			nameA = typeA + "." + nameA
		}
		if functions[j].Recv != nil && len(functions[j].Recv.List) > 0 {
			typeB := fmt.Sprint(functions[j].Recv.List[0].Type)
			nameB = typeB + "." + nameB
		}

		return strings.Compare(nameA, nameB) < 0
	})

	var newDecls []ast.Decl
	newDecls = append(newDecls, others...)
	for _, fn := range inits {
		newDecls = append(newDecls, fn)
	}
	for _, fn := range functions {
		newDecls = append(newDecls, fn)
	}

	node.Decls = newDecls

	printer.Config{Mode: printer.UseSpaces | printer.TabIndent, Tabwidth: 4}.Fprint(os.Stdout, fset, node)
}