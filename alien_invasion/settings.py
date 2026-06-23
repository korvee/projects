class Settings():
    def __init__(self):
        self.screen_width = 900
        self.screen_height = 700
        self.bg_color = (230,230,230)
        
        self.ship_limit = 3

        self.bullet_width = 80
        self.bullet_height = 15
        self.bullet_color = 60,60,60
        self.bullet_allowed = 5

        self.fleet_drop_speed = 20
        
        self.speedup_scale = 1

        self.score_scale = 1.5
        self.alien_points = 50


        self.initialize_dymatic_settings()

    def initialize_dymatic_settings(self):
        self.ship_speed_factor = 1
        self.bullet_speed_factor = 1
        self.alien_speed_factor = 0.1

        self.alien_points = int(self.alien_points * self.score_scale)

    def increase_speed(self):
        self.ship_speed_factor = self.ship_speed_factor
        self.bullet_speed_factor *= self.bullet_speed_factor
        self.alien_speed_factor += 0




