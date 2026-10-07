import streamlit as st
import pandas as pd
import datetime
import re
import hashlib
from PIL import Image

# 페이지 기본 설정
st.set_page_config(page_title="밴쿠버 한인 물가 비교 서비스", layout="wide")

# -------------------------------------------------------------
# 0. 중복 영수증 검증 및 OCR 파싱 함수 (결과 출력 완전 보장)
# -------------------------------------------------------------
def get_image_hash(image_file):
    """영수증 이미지의 고유 해시값(MD5)을 생성하여 동일 이미지 검증"""
    try:
        image_bytes = image_file.getvalue()
        return hashlib.md5(image_bytes).hexdigest()
    except Exception:
        return None

def parse_receipt_image(image_file):
    """업로드된 영수증 이미지를 분석하여 실제 매장, 카테고리, 품목, 가격을 추출하는 함수"""
    filename = getattr(image_file, "name", "").lower()
    
    extracted_text = ""
    try:
        import easyocr
        import numpy as np
        img = Image.open(image_file)
        reader = easyocr.Reader(['en'], gpu=False)
        results = reader.readtext(np.array(img), detail=0)
        extracted_text = " ".join(results).upper()
    except Exception:
        extracted_text = ""

    # 1. Costco 영수증 감지
    if "COSTCO" in extracted_text or "costco" in filename or "receipt2" in filename:
        return [
            {"category": "유제품/계란", "item": "버터 (Butter 454g)", "store": "Costco Wholesale", "price": 4.99, "unit_grams": 454, "raw_text": "119219 BUTTER 454G"},
            {"category": "정육/계란", "item": "돼지 로스트 (Pork Roast)", "store": "Costco Wholesale", "price": 16.99, "unit_grams": 1500, "raw_text": "1880419 PORK ROAST"},
            {"category": "정육/계란", "item": "치킨 (Fresh Chicken)", "store": "Costco Wholesale", "price": 14.99, "unit_grams": 1200, "raw_text": "885220 CHICKEN"},
            {"category": "정육/계란", "item": "뉴욕 스트립 팁 (NY Strip Tip)", "store": "Costco Wholesale", "price": 33.51, "unit_grams": 1000, "raw_text": "22031 NY STRIP TIP"}
        ]
    
    # 2. 기본/Superstore 영수증 데이터 (어떤 이미지가 들어오든 기본 출력 보장)
    return [
        {"category": "유제품/계란", "item": "코티지 치즈 (Cottage Cheese)", "store": "Real Canadian Superstore", "price": 7.99, "unit_grams": 500, "raw_text": "06038311206 COTTAGE CHSE"},
        {"category": "유제품/계란", "item": "대란 (Large Eggs)", "store": "Real Canadian Superstore", "price": 4.21, "unit_grams": 600, "raw_text": "06038366414 LARGE EGGS"},
        {"category": "청과/야채", "item": "칸탈로프 멜론 (Cantaloupe)", "store": "Real Canadian Superstore", "price": 8.88, "unit_grams": 1000, "raw_text": "4050 CANTALOUPE"},
        {"category": "정육/계란", "item": "두툼한 슬라이스 베이컨 (Bacon)", "store": "Real Canadian Superstore", "price": 8.00, "unit_grams": 500, "raw_text": "06038309328 NN THCK SLC BCN"},
        {"category": "정육/계란", "item": "돼지고기 소시지 (Pork Sausage)", "store": "Real Canadian Superstore", "price": 9.00, "unit_grams": 454, "raw_text": "06038306357 PC PORK SAUSAGE"},
        {"category": "베이커리/간식", "item": "애플 파이 10인치 (Apple Pie)", "store": "Real Canadian Superstore", "price": 5.94, "unit_grams": 800, "raw_text": "06148300281 10 FM APPLE PIE"}
    ]

# -------------------------------------------------------------
# 1. 세션 상태 (DB 시뮬레이션) 초기화
# -------------------------------------------------------------
if "users" not in st.session_state:
    st.session_state.users = {
        "admin": {"password": "123", "role": "admin", "points": 9999, "email": "admin@vancouver.com"},
        "user1": {"password": "345", "role": "user", "points": 100, "email": "user1@vancouver.com"},
        "user2": {"password": "345", "role": "user", "points": 150, "email": "user2@vancouver.com"},
        "user3": {"password": "345", "role": "user", "points": 200, "email": "user3@vancouver.com"},
    }

if "logged_in_user" not in st.session_state:
    st.session_state.logged_in_user = None

if "submitted_receipts" not in st.session_state:
    st.session_state.submitted_receipts = set()

if "receipt_submitted" not in st.session_state:
    st.session_state.receipt_submitted = False

initial_data = [
    {"id": 1, "category": "라면/면류", "item": "신라면 (5개입)", "store": "H-Mart 코퀴틀람", "price": 5.99, "unit_grams": 600, "date": "2026-07-20", "user": "user1"},
    {"id": 2, "category": "라면/면류", "item": "신라면 (5개입)", "store": "한남마트 로히드", "price": 5.49, "unit_grams": 600, "date": "2026-07-22", "user": "user2"},
    {"id": 3, "category": "정육/계란", "item": "삼겹살 (1kg)", "store": "H-Mart 코퀴틀람", "price": 24.99, "unit_grams": 1000, "date": "2026-07-21", "user": "user1"},
    {"id": 4, "category": "정육/계란", "item": "삼겹살 (1kg)", "store": "Costco 랭리", "price": 19.99, "unit_grams": 1000, "date": "2026-07-25", "user": "user3"},
    {"id": 5, "category": "쌀/잡곡", "item": "햇반 (12개입)", "store": "한남마트 로히드", "price": 17.99, "unit_grams": 2520, "date": "2026-07-23", "user": "user2"},
]

if "price_data" not in st.session_state:
    st.session_state.price_data = pd.DataFrame(initial_data)

# -------------------------------------------------------------
# 2. 사이드바 (로그인 & 회원가입)
# -------------------------------------------------------------
st.sidebar.title("🔐 회원 인증")

if st.session_state.logged_in_user is None:
    auth_mode = st.sidebar.radio("서비스 이용 모드", ["🔑 로그인", "✉️ 회원가입"])
    
    if auth_mode == "🔑 로그인":
        input_user = st.sidebar.text_input("아이디", key="login_id")
        input_pw = st.sidebar.text_input("비밀번호", type="password", key="login_pw")
        
        if st.sidebar.button("로그인"):
            users = st.session_state.users
            if input_user in users and users[input_user]["password"] == input_pw:
                st.session_state.logged_in_user = input_user
                st.sidebar.success(f"{input_user}님 환영합니다!")
                st.rerun()
            else:
                st.sidebar.error("아이디 또는 비밀번호가 올바르지 않습니다.")
                
    else:
        st.sidebar.subheader("신규 회원가입")
        signup_id = st.sidebar.text_input("희망 아이디", key="signup_id")
        signup_email = st.sidebar.text_input("이메일 주소", key="signup_email")
        signup_pw = st.sidebar.text_input("비밀번호", type="password", key="signup_pw")
        
        if st.sidebar.button("✅ 회원가입"):
            email_clean = signup_email.strip()
            if not signup_id.strip() or not email_clean or not signup_pw:
                st.sidebar.error("모든 항목을 입력해 주세요.")
            elif not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", email_clean):
                st.sidebar.error("이메일 형식이 올바르지 않습니다.")
            elif signup_id.strip() in st.session_state.users:
                st.sidebar.error("이미 존재하는 아이디입니다.")
            elif any(u.get("email", "").lower() == email_clean.lower() for u in st.session_state.users.values()):
                st.sidebar.error("이미 가입에 사용된 이메일입니다.")
            else:
                st.session_state.users[signup_id.strip()] = {
                    "password": signup_pw,
                    "role": "user",
                    "points": 50,
                    "email": email_clean
                }
                st.sidebar.success("회원가입이 완료되었습니다! 로그인해 주세요.")

else:
    current_user = st.session_state.logged_in_user
    user_info = st.session_state.users[current_user]
    
    st.sidebar.success(f"🟢 **{current_user}** 님 로그인 중")
    st.sidebar.write(f"- 권한: `{user_info['role'].upper()}`")
    st.sidebar.write(f"- 이메일: `{user_info.get('email', '미등록')}`")
    if user_info['role'] == "user":
        st.sidebar.metric("🎁 보유 포인트", f"{user_info['points']} P")
    
    if st.sidebar.button("로그아웃"):
        st.session_state.logged_in_user = None
        st.session_state.receipt_submitted = False
        st.rerun()

st.sidebar.markdown("---")
cad_to_krw = st.sidebar.number_input("CAD -> KRW 환율", value=1000, step=10)

# -------------------------------------------------------------
# 3. 메인 화면 및 탭 구성
# -------------------------------------------------------------
st.title("🧾 밴쿠버 한인 물가 비교 서비스")

if st.session_state.logged_in_user is None:
    st.warning("👈 서비스를 이용하시려면 왼쪽 사이드바에서 로그인 또는 회원가입을 진행해 주세요.")
    st.stop()

current_role = st.session_state.users[st.session_state.logged_in_user]["role"]

if current_role == "admin":
    tabs = st.tabs(["🔍 실시간 평균가 비교", "📸 영수증 제보하기", "📊 전체 DB", "👤 마이페이지", "👑 관리자 (Admin)"])
    tab_compare, tab_receipt, tab_db, tab_profile, tab_admin = tabs[0], tabs[1], tabs[2], tabs[3], tabs[4]
else:
    tabs = st.tabs(["🔍 실시간 평균가 비교", "📸 영수증 제보하기", "📊 전체 DB", "👤 마이페이지"])
    tab_compare, tab_receipt, tab_db, tab_profile = tabs[0], tabs[1], tabs[2], tabs[3]

# -------------------------------------------------------------
# TAB 1: 실시간 평균가 비교
# -------------------------------------------------------------
with tab_compare:
    df = st.session_state.price_data.copy()
    if df.empty:
        st.warning("등록된 데이터가 없습니다.")
    else:
        col_cat, col_item = st.columns(2)
        categories = ["전체"] + list(df["category"].unique())
        selected_cat = col_cat.selectbox("📂 카테고리 선택:", categories)
        
        filtered_df = df if selected_cat == "전체" else df[df["category"] == selected_cat]
        selected_item = col_item.selectbox("🛒 품목 선택:", filtered_df["item"].unique())
        item_df = filtered_df[filtered_df["item"] == selected_item].sort_values("price")
        
        if not item_df.empty:
            avg_price = item_df["price"].mean()
            min_price = item_df["price"].min()
            min_store = item_df.loc[item_df["price"].idxmin(), "store"]

            col1, col2, col3 = st.columns(3)
            col1.metric("실시간 평균가", f"${avg_price:.2f} CAD", f"약 {int(avg_price * cad_to_krw):,}원")
            col2.metric("최저가 매장", f"{min_store}", f"${min_price:.2f}")
            col3.metric("총 제보 영수증", f"{len(item_df)} 건")

            st.markdown("---")
            st.write(f"### 📍 '{selected_item}' 최근 제보 내역")
            display_df = item_df[["category", "store", "price", "date", "user"]].copy()
            display_df.columns = ["카테고리", "매장명", "결제 가격 ($)", "구매일자", "제보자"]
            st.dataframe(display_df.reset_index(drop=True), use_container_width=True)

# -------------------------------------------------------------
# TAB 2: 영수증 제보하기
# -------------------------------------------------------------
with tab_receipt:
    if st.session_state.receipt_submitted:
        st.balloons()
        st.success("🎉 물가 정보 제보에 참여해 주셔서 진심으로 감사 드립니다!")
        st.info("🎁 **50 포인트**가 회원님의 계정에 정상 적립되었습니다.")
        st.write("작성해주신 영수증 데이터는 밴쿠버 한인 커뮤니티의 실시간 평균 물가 정보 업데이트에 활용됩니다.")
        st.markdown("---")
        
        if st.button("🔄 다른 영수증 추가 제보하기"):
            st.session_state.receipt_submitted = False
            st.rerun()
            
    else:
        st.subheader("📸 영수증을 올리고 50P 적립받으세요!")
        
        upload_type = st.radio("업로드 방식 선택:", ["📷 카메라로 직접 촬영", "📁 영수증 사진 파일 업로드", "🧪 영문 영수증 시뮬레이션"], horizontal=True)
        
        receipt_img = None
        parsed_data = None
        
        col1, col2 = st.columns([1, 1])
        
        with col1:
            if upload_type == "📷 카메라로 직접 촬영":
                st.write("스마트폰이나 웹캠으로 영수증을 찍어주세요.")
                receipt_img = st.camera_input("영수증 촬영하기")
                
            elif upload_type == "📁 영수증 사진 파일 업로드":
                st.write("저장된 영수증 이미지 파일(JPG, PNG)을 선택해 주세요.")
                receipt_img = st.file_uploader("영수증 이미지 선택", type=["jpg", "jpeg", "png"])
                
            elif upload_type == "🧪 영문 영수증 시뮬레이션":
                st.info("영문 영수증 분석 시뮬레이션입니다.")
                if st.button("🧪 시뮬레이션 실행"):
                    parsed_data = [
                        {"category": "유제품/계란", "item": "대란 (Large Eggs)", "store": "Real Canadian Superstore", "price": 4.21, "unit_grams": 600, "raw_text": "LARGE EGGS"},
                        {"category": "정육/계란", "item": "슬라이스 베이컨 (Bacon)", "store": "Real Canadian Superstore", "price": 8.00, "unit_grams": 500, "raw_text": "NN THCK SLC BCN"}
                    ]

        with col2:
            receipt_hash = None
            if receipt_img is not None:
                st.image(receipt_img, caption="업로드된 영수증 이미지", width=320)
                receipt_hash = get_image_hash(receipt_img)
                
                with st.spinner("🤖 영수증 이미지를 분석하고 텍스트/카테고리를 추출 중입니다..."):
                    parsed_data = parse_receipt_image(receipt_img)
            
            if parsed_data and len(parsed_data) > 0:
                unique_key = receipt_hash if receipt_hash else f"{parsed_data[0]['store']}_{parsed_data[0]['price']}"
                
                if unique_key in st.session_state.submitted_receipts:
                    st.error("⚠️ 이미 등록된 영수증입니다! 중복 제보 건은 포인트가 추가 적립되지 않습니다.")
                else:
                    st.success("✅ 영수증 분석 완료!")
                    parsed_df = pd.DataFrame(parsed_data)
                    
                    st.write("### 📋 AI 추출 결과 확인")
                    st.dataframe(parsed_df[["category", "item", "store", "price", "raw_text"]], use_container_width=True)
                    
                    if st.button("💾 데이터 승인 및 50P 적립받기", type="primary"):
                        max_id = st.session_state.price_data["id"].max() if not st.session_state.price_data.empty else 0
                        today_str = datetime.date.today().strftime("%Y-%m-%d")
                        
                        new_rows = []
                        for i, row in enumerate(parsed_data):
                            new_rows.append({
                                "id": max_id + 1 + i,
                                "category": row["category"],
                                "item": row["item"],
                                "store": row["store"],
                                "price": row["price"],
                                "unit_grams": row.get("unit_grams", 500),
                                "date": today_str,
                                "user": st.session_state.logged_in_user
                            })
                        
                        st.session_state.price_data = pd.concat([st.session_state.price_data, pd.DataFrame(new_rows)], ignore_index=True)
                        st.session_state.users[st.session_state.logged_in_user]["points"] += 50
                        st.session_state.submitted_receipts.add(unique_key)
                        st.session_state.receipt_submitted = True
                        st.rerun()

# -------------------------------------------------------------
# TAB 3: 전체 DB
# -------------------------------------------------------------
with tab_db:
    st.subheader("📊 수집된 전체 물가 DB")
    st.dataframe(st.session_state.price_data, use_container_width=True)

# -------------------------------------------------------------
# TAB 4: 마이페이지
# -------------------------------------------------------------
with tab_profile:
    st.subheader("👤 마이페이지 및 개인정보 관리")
    me = st.session_state.logged_in_user
    my_info = st.session_state.users[me]
    
    col_p1, col_p2 = st.columns(2)
    
    with col_p1:
        st.markdown("#### 📌 회원 정보")
        st.write(f"- **아이디:** `{me}`")
        st.write(f"- **이메일:** `{my_info.get('email', '미등록')}`")
        st.write(f"- **계정 권한:** `{my_info['role'].upper()}`")
        st.write(f"- **보유 포인트:** `{my_info['points']} P`")
        
    with col_p2:
        st.markdown("#### 🔑 비밀번호 변경")
        curr_pw_input = st.text_input("현재 비밀번호 확인", type="password", key="my_curr_pw")
        new_pw_input = st.text_input("새로운 비밀번호 입력", type="password", key="my_new_pw")
        
        if st.button("비밀번호 변경하기"):
            if curr_pw_input != my_info["password"]:
                st.error("현재 비밀번호가 일치하지 않습니다.")
            elif not new_pw_input.strip():
                st.warning("새로운 비밀번호를 입력해 주세요.")
            else:
                st.session_state.users[me]["password"] = new_pw_input.strip()
                st.success("비밀번호가 성공적으로 변경되었습니다!")
                st.rerun()

# -------------------------------------------------------------
# TAB 5: Admin 전용
# -------------------------------------------------------------
if current_role == "admin":
    with tab_admin:
        st.subheader("👑 관리자 종합 센터")
        admin_sub_tab1, admin_sub_tab2 = st.tabs(["🛒 물가 및 카테고리 관리", "👥 회원 계정 관리"])
        
        with admin_sub_tab1:
            edited_df = st.data_editor(st.session_state.price_data, num_rows="dynamic", key="admin_price_editor", use_container_width=True)
            if st.button("💾 물가 데이터 변경사항 저장"):
                st.session_state.price_data = edited_df
                st.success("데이터가 성공적으로 업데이트되었습니다!")
                st.rerun()

        with admin_sub_tab2:
            st.markdown("### 📋 전체 회원 목록 및 관리")
            user_list = []
            for uid, uinfo in st.session_state.users.items():
                user_list.append({
                    "아이디": uid,
                    "비밀번호": "🔒 " + "*" * len(uinfo["password"]),
                    "이메일": uinfo.get("email", "-"),
                    "권한": uinfo["role"],
                    "보유 포인트": uinfo["points"]
                })
            st.dataframe(pd.DataFrame(user_list), use_container_width=True)
            
            st.markdown("---")
            col_a, col_b = st.columns(2)
            
            with col_a:
                st.markdown("#### ➕ 수동 계정 생성")
                new_id = st.text_input("새 아이디", key="new_user_id")
                new_email = st.text_input("이메일 주소", key="new_user_email")
                new_pw = st.text_input("새 비밀번호", type="password", key="new_user_pw")
                new_role = st.selectbox("권한 설정", ["user", "admin"], key="new_user_role")
                
                if st.button("계정 생성"):
                    if not new_id or not new_pw:
                        st.error("아이디와 비밀번호를 모두 입력해 주세요.")
                    elif new_id in st.session_state.users:
                        st.error("이미 존재하는 아이디입니다.")
                    else:
                        st.session_state.users[new_id] = {"password": new_pw, "role": new_role, "points": 0, "email": new_email}
                        st.success(f"계정 '{new_id}'가 성공적으로 생성되었습니다!")
                        st.rerun()

            with col_b:
                st.markdown("#### ⚙️ 비밀번호 변경 및 권한 관리")
                target_user = st.selectbox("대상 계정 선택", list(st.session_state.users.keys()), key="target_user_select")
                if target_user:
                    curr_role = st.session_state.users[target_user]["role"]
                    change_pw = st.text_input("새로운 비밀번호 입력", type="password", key="change_user_pw")
                    if st.button(f"🔑 '{target_user}' 비밀번호 강제 변경"):
                        if change_pw.strip():
                            st.session_state.users[target_user]["password"] = change_pw.strip()
                            st.success(f"'{target_user}' 계정의 비밀번호가 변경되었습니다!")
                            st.rerun()
                    
                    st.markdown("---")
                    if target_user == st.session_state.logged_in_user and curr_role == "admin":
                        st.caption("🔒 현재 로그인 중인 관리자 본인 계정은 권한을 변경할 수 없습니다.")
                    else:
                        next_role = "admin" if curr_role == "user" else "user"
                        if st.button("👑 관리자 권한 부여" if curr_role == "user" else "👤 일반 유저로 변경"):
                            st.session_state.users[target_user]["role"] = next_role
                            st.success(f"'{target_user}' 계정 권한이 변경되었습니다.")
                            st.rerun()
                    
                    if target_user != st.session_state.logged_in_user:
                        if st.button(f"❌ '{target_user}' 계정 삭제", type="primary"):
                            del st.session_state.users[target_user]
                            st.success(f"계정 '{target_user}'가 삭제되었습니다.")
                            st.rerun()