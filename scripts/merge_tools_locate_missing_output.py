import os
import csv
import shutil

# configuration
script_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(script_dir)
ROOT_DIR = os.path.join(project_root, 'mergeAnalysisOutput') #results folder
OUTPUT_CSV_NAME = 'failedScenarios.csv'

def check_scenario(scenario_path, relative_path, target_filename):
    std_path = os.path.join(scenario_path, 'mergiraf', target_filename)
    semi_c_path = os.path.join(scenario_path, 'mergiraf-semi-c', target_filename)
    semi_sc_path = os.path.join(scenario_path, 'mergiraf-semi-sc', target_filename)

    # true if file exists and has content
    std_ok = os.path.exists(std_path) and os.path.getsize(std_path) > 0
    semi_c_ok = os.path.exists(semi_c_path) and os.path.getsize(semi_c_path) > 0
    semi_sc_ok = os.path.exists(semi_sc_path) and os.path.getsize(semi_sc_path) > 0

    # if both semi ok, doesnt report
    if semi_c_ok and semi_sc_ok:
        return []
    
    path_parts = relative_path.split(os.sep)
    project = path_parts[0] if len(path_parts) >= 1 else "unknown"
    commit = path_parts[1] if len(path_parts) >= 2 else "unknown"
    
    # get file path
    file_path = os.path.join(*path_parts[2:]) if len(path_parts) > 2 else relative_path

    # error type classsifier
    error_type = ""
    if not semi_c_ok and not semi_sc_ok:
        error_type = "BOTH SEMI FAILED"
    elif not semi_c_ok:
        error_type = "SEMI-C FAILED"
    elif not semi_sc_ok:
        error_type = "SEMI-SC FAILED"

    issue = {
        'project': project,
        'commit': commit,
        'file_path': file_path,
        'status_mergiraf': 'OK' if std_ok else 'FAILED',
        'status_semi_c': 'OK' if semi_c_ok else 'FAILED',
        'status_semi_sc': 'OK' if semi_sc_ok else 'FAILED',
        'error_type': error_type
    }

    return [issue]

def main():
    print("-" * 50)
    user_ext = input("Type file extension (eg: java, js, ts): ").strip().lower()
    
    if user_ext.startswith('.'):
        user_ext = user_ext[1:]
    
    if not user_ext:
        print("No extension typed. using default: 'java'.")
        user_ext = "java"

    target_filename = f"merge.{user_ext}"
    
    output_csv_name = f"failedScenarios_{user_ext}.csv"
    output_csv_path = os.path.join(ROOT_DIR, output_csv_name)

    scenarios_output_dir = os.path.join(ROOT_DIR, f"scenarios_failed_{user_ext}")

    if not os.path.exists(ROOT_DIR):
        print(f"CRITICAL ERROR: Data folder not found in: {ROOT_DIR}\n")
        return

    output_csv_path = os.path.join(ROOT_DIR, OUTPUT_CSV_NAME)
    columns = ['project', 'commit', 'file_path', 
               'status_mergiraf', 'status_semi_c', 'status_semi_sc', 'error_type']

    print(f"creating report file: {OUTPUT_CSV_NAME}")
    with open(output_csv_path, 'w', newline='', encoding='utf-8') as f:
        dict_writer = csv.DictWriter(f, fieldnames=columns)
        dict_writer.writeheader()
    
    print(f"Starting search in: {ROOT_DIR}")
    print("-" * 50)
    
    results = []
    found_scenarios = 0
    problematic_scenarios = 0

    current_project = None
    current_commit = None
    
    for root, dirs, files in os.walk(ROOT_DIR):
        # search for tool folders
        if f"scenarios_failed_{user_ext}" in root:
            continue

        if 'mergiraf' in dirs and ('mergiraf-semi-c' in dirs or 'mergiraf-semi-sc' in dirs):
            
            found_scenarios += 1
            relative_path = os.path.relpath(root, ROOT_DIR)

            parts = relative_path.split(os.sep)
            if len(parts) >= 2:
                project_name = parts[0]
                commit_name = parts[1]

                if project_name != current_project:
                    print(f"--> Starting repository: {project_name}")
                    current_project = project_name
                    current_commit = None
                
                if commit_name != current_commit:
                    print(f"----> Commit: {commit_name}")
                    current_commit = commit_name


            issues = check_scenario(root, relative_path, target_filename)

            if issues:
                problematic_scenarios += 1
                with open(output_csv_path, 'a', newline='', encoding='utf-8') as f:
                    dict_writer = csv.DictWriter(f, fieldnames=columns)
                    dict_writer.writerows(issues)
                print(f"------> [!] Failure registered in commit {issues[0]['commit'][:8]}...")

                dest_dir = os.path.join(scenarios_output_dir, relative_path)
                try:
                    shutil.copytree(root, dest_dir, dirs_exist_ok=True)
                    print(f"        [!] FALHA - Cenário copiado para análise.")
                except Exception as e:
                    print(f"        [X] Erro ao copiar pasta: {e}")

    print("-" * 50)
    print(f"Processo finalizado!")
    print(f"Total verificado: {found_scenarios}")
    print(f"Total com falhas salvos no CSV: {problematic_scenarios}")
    print(f"Arquivo final: {output_csv_path}")

if __name__ == "__main__":
    main()