from flask import Flask, render_template, request, jsonify, redirect, url_for, flash
from flask_sqlalchemy import SQLAlchemy
from flask_login import LoginManager, UserMixin, login_user, login_required, logout_user, current_user
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime
import os

app = Flask(__name__)
app.config['SECRET_KEY'] = 'gharfix-secret-key-2024'
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///gharfix.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)
login_manager = LoginManager()
login_manager.init_app(app)
login_manager.login_view = 'login'

# ============== DATABASE MODELS ==============


class User(UserMixin, db.Model):
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(100), nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=False)
    phone = db.Column(db.String(20), nullable=False)
    password_hash = db.Column(db.String(200), nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    bookings = db.relationship('Booking', backref='user', lazy=True)

    def set_password(self, password):
        self.password_hash = generate_password_hash(password)

    def check_password(self, password):
        return check_password_hash(self.password_hash, password)


class Worker(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(100), nullable=False)
    service = db.Column(db.String(50), nullable=False)
    phone = db.Column(db.String(20), nullable=False)
    rating = db.Column(db.Float, default=4.0)
    image_url = db.Column(db.String(200))
    experience = db.Column(db.String(50))
    description = db.Column(db.Text)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    bookings = db.relationship('Booking', backref='worker', lazy=True)


class Service(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(50), nullable=False)
    icon = db.Column(db.String(50), nullable=False)
    description = db.Column(db.String(200))


class Booking(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    worker_id = db.Column(db.Integer, db.ForeignKey(
        'worker.id'), nullable=False)
    service_name = db.Column(db.String(50), nullable=False)
    address = db.Column(db.String(200), nullable=False)
    city = db.Column(db.String(50), nullable=False)
    booking_date = db.Column(db.Date, nullable=False)
    booking_time = db.Column(db.Time, nullable=False)
    status = db.Column(db.String(20), default='pending')
    created_at = db.Column(db.DateTime, default=datetime.utcnow)


class Review(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    worker_id = db.Column(db.Integer, db.ForeignKey(
        'worker.id'), nullable=False)
    rating = db.Column(db.Float, nullable=False)
    comment = db.Column(db.Text)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)


@login_manager.user_loader
def load_user(user_id):
    return User.query.get(int(user_id))

# ============== ROUTES ==============


@app.route('/')
def index():
    services = Service.query.all()
    workers = Worker.query.order_by(Worker.rating.desc()).limit(6).all()
    reviews = Review.query.order_by(Review.created_at.desc()).limit(3).all()
    return render_template('index.html', services=services, workers=workers, reviews=reviews)


@app.route('/register', methods=['GET', 'POST'])
def register():
    if request.method == 'POST':
        data = request.form
        if User.query.filter_by(email=data['email']).first():
            flash('Email already registered!', 'error')
            return redirect(url_for('index'))

        user = User(
            name=data['name'],
            email=data['email'],
            phone=data['phone']
        )
        user.set_password(data['password'])
        db.session.add(user)
        db.session.commit()

        flash('Account created successfully! Please login.', 'success')
        return redirect(url_for('index'))
    return redirect(url_for('index'))


@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        data = request.form
        user = User.query.filter_by(email=data['email']).first()

        if user and user.check_password(data['password']):
            login_user(user)
            flash('Logged in successfully!', 'success')
            return redirect(url_for('index'))
        else:
            flash('Invalid email or password!', 'error')
            return redirect(url_for('index'))
    return redirect(url_for('index'))


@app.route('/logout')
@login_required
def logout():
    logout_user()
    flash('Logged out successfully!', 'success')
    return redirect(url_for('index'))


@app.route('/book', methods=['POST'])
@login_required
def book_service():
    try:
        data = request.form
        worker_id = request.form.get('worker_id')

        if not worker_id:
            flash('Please select a worker.', 'error')
            return redirect(url_for('index'))

        booking = Booking(
            user_id=current_user.id,
            worker_id=int(worker_id),
            service_name=data['service'],
            address=data['address'],
            city=data['city'],
            booking_date=datetime.strptime(data['date'], '%Y-%m-%d').date(),
            booking_time=datetime.strptime(data['time'], '%H:%M').time()
        )
        db.session.add(booking)
        db.session.commit()

        flash('Booking confirmed! We will contact you soon.', 'success')
    except Exception as e:
        db.session.rollback()
        flash(f'Booking failed: {str(e)}', 'error')
    return redirect(url_for('index'))


@app.route('/my-bookings')
@login_required
def my_bookings():
    bookings = Booking.query.filter_by(user_id=current_user.id).order_by(
        Booking.booking_date.desc()).all()
    return render_template('bookings.html', bookings=bookings)

# ============== API ROUTES ==============


@app.route('/api/services')
def api_services():
    services = Service.query.all()
    return jsonify([{
        'id': s.id,
        'name': s.name,
        'icon': s.icon,
        'description': s.description
    } for s in services])


@app.route('/api/workers')
def api_workers():
    service = request.args.get('service')
    query = Worker.query
    if service:
        query = query.filter_by(service=service)
    workers = query.order_by(Worker.rating.desc()).all()
    return jsonify([{
        'id': w.id,
        'name': w.name,
        'service': w.service,
        'rating': w.rating,
        'image_url': w.image_url,
        'experience': w.experience
    } for w in workers])


@app.route('/api/search')
def api_search():
    query = request.args.get('q', '')
    workers = Worker.query.filter(Worker.name.ilike(
        f'%{query}%') | Worker.service.ilike(f'%{query}%')).all()
    return jsonify([{
        'id': w.id,
        'name': w.name,
        'service': w.service,
        'rating': w.rating
    } for w in workers])

# ============== SEED DATA ==============


def seed_database():
    # Seed Services
    services_data = [
        {'name': 'Electrician', 'icon': 'fa-bolt',
            'description': 'Electrical repairs and installations'},
        {'name': 'Plumber', 'icon': 'fa-wrench',
            'description': 'Pipe fitting and water repairs'},
        {'name': 'Carpenter', 'icon': 'fa-hammer',
            'description': 'Furniture and woodwork'},
        {'name': 'AC Service', 'icon': 'fa-snowflake',
            'description': 'Air conditioner repair and service'},
        {'name': 'House Maid', 'icon': 'fa-broom',
            'description': 'Home cleaning services'},
        {'name': 'Laundry', 'icon': 'fa-shirt',
            'description': 'Cloth washing and ironing'},
        {'name': 'Labour', 'icon': 'fa-person-digging',
            'description': 'General labor work'},
        {'name': 'Contractor', 'icon': 'fa-building',
            'description': 'Construction and renovation'}
    ]

    for s in services_data:
        if not Service.query.filter_by(name=s['name']).first():
            service = Service(**s)
            db.session.add(service)

    # Seed Workers
    workers_data = [
        {'name': 'Ramesh Kumar', 'service': 'Plumber', 'phone': '9876543210', 'rating': 4.5, 'image_url': 'https://randomuser.me/api/portraits/men/32.jpg',
            'experience': '5 years', 'description': 'Expert in pipe fitting and leak repairs'},
        {'name': 'Suresh Patel', 'service': 'Electrician', 'phone': '9876543211', 'rating': 4.7, 'image_url': 'https://randomuser.me/api/portraits/men/45.jpg',
            'experience': '7 years', 'description': 'Specialist in wiring and electrical installations'},
        {'name': 'Amit Sharma', 'service': 'Carpenter', 'phone': '9876543212', 'rating': 4.6,
            'image_url': 'https://randomuser.me/api/portraits/men/60.jpg', 'experience': '8 years', 'description': 'Expert furniture maker and repairs'},
        {'name': 'Raj Malhotra', 'service': 'AC Service', 'phone': '9876543213', 'rating': 4.8,
            'image_url': 'https://randomuser.me/api/portraits/men/22.jpg', 'experience': '6 years', 'description': 'AC repair and maintenance expert'},
        {'name': 'Priya Singh', 'service': 'House Maid', 'phone': '9876543214', 'rating': 4.9,
            'image_url': 'https://randomuser.me/api/portraits/women/44.jpg', 'experience': '4 years', 'description': 'Professional home cleaning services'},
        {'name': 'Vikram Joshi', 'service': 'Laundry', 'phone': '9876543215', 'rating': 4.4,
            'image_url': 'https://randomuser.me/api/portraits/men/75.jpg', 'experience': '3 years', 'description': 'Quality laundry and dry cleaning'}
    ]

    for w in workers_data:
        if not Worker.query.filter_by(name=w['name']).first():
            worker = Worker(**w)
            db.session.add(worker)

    # Seed Reviews (only if at least one user exists)
    first_user = User.query.first()
    if first_user:
        reviews_data = [
            {'user_id': first_user.id, 'worker_id': 1, 'rating': 5.0,
                'comment': 'Great service! Electrician arrived on time.'},
            {'user_id': first_user.id, 'worker_id': 2, 'rating': 4.5,
                'comment': 'Very professional plumber, fixed everything quickly.'},
            {'user_id': first_user.id, 'worker_id': 3, 'rating': 4.7,
                'comment': 'Affordable and reliable service.'}
        ]

        for r in reviews_data:
            if not Review.query.filter_by(comment=r['comment']).first():
                review = Review(**r)
                db.session.add(review)

    db.session.commit()
    print("Database seeded successfully!")

# ============== INIT ==============


if __name__ == '__main__':
    with app.app_context():
        db.create_all()
        seed_database()
    app.run(debug=False, host='0.0.0.0', port=int(os.environ.get('PORT', 5000)))
