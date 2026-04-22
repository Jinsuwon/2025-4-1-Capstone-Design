import os
import time
import threading
from PIL import Image, ImageTk, ImageDraw, ImageFont
import serial
import tkinter as tk
from itertools import cycle

# 시리얼 설정
#PORT = 'COM3'
#BAUD_RATE = 9600

# 광고 이미지 경로 설정
AD_FOLDER = 'ads'
DISPLAY_TIME = 2

ad_images = [os.path.join(AD_FOLDER, img) for img in os.listdir(AD_FOLDER)
             if img.lower().endswith(('.jpg', '.png'))]
ad_cycle = cycle(ad_images)

# GUI 설정
root = tk.Tk()
root.title("버스 정류장 안내 시스템")
screen_width = root.winfo_screenwidth()
screen_height = root.winfo_screenheight()
root.geometry(f"{screen_width}x{screen_height}")
root.configure(bg='black')
canvas = tk.Canvas(root, width=screen_width, height=screen_height)
canvas.pack()

# 광고 제어 플래그
ad_active = True

def show_ad_image(img_path):
    img = Image.open(img_path).resize((screen_width, screen_height))
    tk_img = ImageTk.PhotoImage(img)
    canvas.delete("all")
    canvas.create_image(0, 0, anchor=tk.NW, image=tk_img)
    canvas.image = tk_img

def show_bus_info(bus_number):
    img = Image.new("RGB", (screen_width, screen_height), color="yellow")  # 🔄 배경색 yellow
    draw = ImageDraw.Draw(img)
    try:
        font_path = "C:/Windows/Fonts/malgunbd.ttf"
        font = ImageFont.truetype(font_path, 400)  # 🔄 글자 크기 400
    except:
        font = ImageFont.load_default()

    message = f"{bus_number}번"
    bbox = draw.textbbox((0, 0), message, font=font)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]

    draw.text(((screen_width - w) / 2, (screen_height - h) / 2), message, fill="black", font=font)  # 🔄 글자색 black
    tk_img = ImageTk.PhotoImage(img)
    canvas.delete("all")
    canvas.create_image(0, 0, anchor=tk.NW, image=tk_img)
    canvas.image = tk_img


def ad_loop():
    while True:
        if ad_active:
            show_ad_image(next(ad_cycle))
        time.sleep(DISPLAY_TIME)

def serial_loop():
    global ad_active
    try:
        ser = serial.Serial(PORT, BAUD_RATE, timeout=1)
        print(f"📡 Bluetooth 포트({PORT}) 연결됨")

        while True:
            ad_active = True
            print("🎞️ 광고 재생 중")
            ser.write("부산시청\n".encode())

            while True:
                if ser.in_waiting:
                    message = ser.readline().decode().strip()
                    print(f"📩 수신 메시지: {repr(message)}")

                    if message.lower() == "exit":
                        print("📴 앱 종료 감지 → 광고 재시작")
                        ad_active = True
                        break

                    elif message.lower() == "advertise":
                        print("🔄 광고 재생 신호 수신 → 광고 모드 진입")
                        ad_active = True
                        break

                    else:
                        bus_number = message.strip()
                        print(f"🚌 받은 버스 번호: {bus_number}")
                        ad_active = False
                        show_bus_info(bus_number)

    except serial.SerialException as e:
        print(f"❌ 시리얼 오류: {e}")

# 스레드 시작
threading.Thread(target=ad_loop, daemon=True).start()
threading.Thread(target=serial_loop, daemon=True).start()

root.mainloop()
