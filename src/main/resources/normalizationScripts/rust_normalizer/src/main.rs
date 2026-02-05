use std::env;
use std::fs;
use std::io::{self, Write};
use syn::{parse_file, Item, ItemImpl, ImplItem};
use quote::ToTokens;

fn main() -> anyhow::Result<()> {
    let args: Vec<String> = env::args().collect();
    if args.len() < 2 {
        eprintln!("Uso: cargo run -- <arquivo>");
        std::process::exit(1);
    }

    let path = &args[1];
    let content = fs::read_to_string(path)?;

    // 1. Parse do arquivo completo
    let mut file = parse_file(&content)?;

    // 2. Percorre os itens do arquivo
    for item in &mut file.items {
        if let Item::Impl(item_impl) = item {
            // Se for um bloco 'impl' (onde métodos vivem)
            sort_impl_items(item_impl);
        }
    }

    // 3. Imprime o código reconstruído
    let formatted = file.into_token_stream().to_string();
    // O 'quote' gera tudo numa linha só as vezes, então idealmente
    // passariamos por um formatador (rustfmt), mas para normalização
    // de comparação, tokens na mesma ordem já basta se removermos espaços depois.
    println!("{}", formatted);

    Ok(())
}

fn sort_impl_items(item_impl: &mut ItemImpl) {
    // Separa métodos do resto (constantes, tipos associados)
    let (mut methods, mut others): (Vec<_>, Vec<_>) = item_impl.items.drain(..).partition(|i| {
        matches!(i, ImplItem::Fn(_))
    });

    // Ordena métodos pelo nome
    methods.sort_by(|a, b| {
        let name_a = get_method_name(a);
        let name_b = get_method_name(b);
        name_a.cmp(&name_b)
    });

    // Reconstrói
    item_impl.items.extend(others);
    item_impl.items.extend(methods);
}

fn get_method_name(item: &ImplItem) -> String {
    if let ImplItem::Fn(method) = item {
        method.sig.ident.to_string()
    } else {
        String::new()
    }
}