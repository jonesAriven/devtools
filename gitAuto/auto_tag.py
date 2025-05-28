import os
import subprocess

def check_repo_exists(repo_path, repo_url):
    if not os.path.exists(repo_path):
        print(f"{repo_path} repository does not exist, cloning...")
        result = subprocess.run(['git', 'clone', repo_url, repo_path])
        if result.returncode != 0:
            print("Failed to clone repository. Error details:")
            print("1. Check your network connection.")
            print("2. Verify the repository address is correct.")
            print("3. Ensure Git is properly installed and configured.")
            exit(1)

def input_iteration_date():
    while True:
        iteration = input("Please enter the iteration date (e.g., 20250605) or type 'exit' to quit: ")
        if iteration.lower() == 'exit':
            print("Exiting script...")
            exit(0)
        if iteration.isdigit() and len(iteration) == 8:
            return iteration
        else:
            print("Incorrect date format!")

def input_release_reason():
    reason = input("Please enter the release reason (e.g., lt, release) or type 'exit' to quit: ")
    if reason.lower() == 'exit':
        print("Exiting script...")
        exit(0)
    return reason

def overwrite_branches(version):
    repos = [
        ('ngcard', 'lt-ngcard', f'release-ngcard-{version}'),
        ('ngcardh5', 'lt-ngcardh5', f'release-ngcardh5-{version}'),
        ('ngcardbo', 'lt-ngcardbo', f'release-ngcardbo-{version}')
    ]
    for repo_name, lt_branch, release_branch in repos:
        repo_path = os.path.join('D:\\huliang\\cursorProjects', repo_name)
        os.chdir(repo_path)
        result = subprocess.run(['git', 'fetch', 'origin', f'{release_branch}:{lt_branch}'])
        if result.returncode != 0:
            print(f"Failed to fetch {repo_name} branch, please check if the remote branch exists.")
            exit(1)
        result = subprocess.run(['git', 'checkout', lt_branch])
        if result.returncode != 0:
            print(f"Failed to switch to {repo_name} branch, please check if the branch exists.")
            exit(1)
        result = subprocess.run(['git', 'push', 'origin', lt_branch, '--force'])
        if result.returncode != 0:
            print(f"Failed to force push {repo_name} branch, please check permissions or network.")
            exit(1)

def process_tag(app_name, branch_type, version):
    tag = input(f"Please enter the tag number for {app_name} application or type 'exit' to quit: ")
    if tag.lower() == 'exit':
        print("Exiting script...")
        exit(0)
    repo_path = os.path.join('D:\\huliang\\cursorProjects', app_name)
    os.chdir(repo_path)
    if branch_type == 'lt':
        branch = f'lt-{app_name}'
    else:
        branch = f'release-{app_name}-{version}'
    result = subprocess.run(['git', 'checkout', branch])
    if result.returncode != 0:
        print(f"Failed to checkout {branch} branch.")
        exit(1)
    result = subprocess.run(['git', 'pull', 'origin', branch])
    if result.returncode != 0:
        print(f"Failed to pull {branch} branch, please check network or branch existence.")
        exit(1)
    result = subprocess.run(['git', 'tag', '-a', f'v{tag}', '-m', reason])
    if result.returncode != 0:
        print(f"Failed to create tag v{tag}.")
        exit(1)
    result = subprocess.run(['git', 'push', 'origin', f'v{tag}'])
    if result.returncode != 0:
        print(f"Failed to push tag v{tag}, please check if the tag already exists or network issues.")
        exit(1)

if __name__ == "__main__":
    NGCARD_PATH = 'D:\\huliang\\cursorProjects\\ngcard'
    NGCARDH5_PATH = 'D:\\huliang\\cursorProjects\\ngcardh5'
    NGCARDBO_PATH = 'D:\\huliang\\cursorProjects\\ngcardbo'

    NGCARD_REPO = 'https://gitee.com/jonesAriven/ngcard.git'
    NGCARDH5_REPO = 'https://gitee.com/jonesAriven/ngcardh5.git'
    NGCARDBO_REPO = 'https://gitee.com/jonesAriven/ngcardbo.git'

    check_repo_exists(NGCARD_PATH, NGCARD_REPO)
    check_repo_exists(NGCARDH5_PATH, NGCARDH5_REPO)
    check_repo_exists(NGCARDBO_PATH, NGCARDBO_REPO)

    iteration = input_iteration_date()
    reason = input_release_reason()

    overwrite_branches(iteration)

    process_tag('ngcard', 'lt', iteration)
    process_tag('ngcardh5', 'lt', iteration)
    process_tag('ngcardbo', 'lt', iteration)

    print("All operations completed")