🥑 Nutrease
Streamlit application designed for nutrition patients and professionals.
Log meals and symptoms, calculate nutrient intake, set reminders, connect with specialists, and chat in a single interface.

✨ Key Features
Food Diary: Add, edit, or remove meals and symptoms with automatic lactose, sorbitol, and gluten calculations.

Food Dataset: Fuzzy search and unit conversions powered by a customizable CSV (data/alimentazione_demo.csv provided for demo).

Diary Reminders: Configure daily alerts to remember entries.

Specialist Connections:

Patients can send connection requests to one or multiple specialists.

Specialists approve or decline requests and view linked patients’ diaries.

Patient–Specialist Chat with persistent history.

Profile Management: Update personal data, change passwords, or delete accounts.

Lightweight Persistence via TinyDB (JSON database in nutrease_db.json).

🚀 Installation
git clone https://github.com/USERNAME/Nutrease.git
cd Nutrease
python -m venv .venv && source .venv/bin/activate      # optional but recommended
pip install -r requirements.txt
To use a custom food dataset, set the environment variable
NUTREASE_DATASET_PATH to your CSV file path.

▶️ Run the App
streamlit run streamlit_app.py
Streamlit will display the URL where the interface is available (usually http://localhost:8501).

💾 Persistence

Application data are stored locally in 'nutrease_db.json'.  The file is
ignored by version control so records remain available across app restarts and
repository updates.  Remove the file or use the application's delete commands
to reset the stored data.

🧪 Testing & Code Quality
pytest                 # run the test suite
pre-commit run --all   # format & lint (black, isort, flake8, etc.)

📁 Project Structure
Nutrease/
├── assets/                  # logos and static resources
├── data/                    # demo food dataset CSV
├── nutrease/
│   ├── controllers/         # application logic (patient, specialist, messaging)
│   ├── models/              # domain dataclasses (diaries, records, users, etc.)
│   ├── services/            # support services (auth, dataset, notifications)
│   ├── ui/                  # Streamlit pages
│   └── utils/               # helpers (database, timezone)
├── tests/                   # unit tests
└── streamlit_app.py         # main entry point
📄 License
Distributed under the Apache 2.0 license—see LICENSE for details.

✨ Happy tracking with Nutrease!

