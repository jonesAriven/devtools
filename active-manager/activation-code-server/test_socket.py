import socket

try:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    result = sock.connect_ex(('192.168.31.182', 3306))
    if result == 0:
        print("Port 3306 is open")
    else:
        print(f"Port 3306 is not reachable. Error code: {result}")
    sock.close()
except Exception as e:
    print(f"Error: {e}")
