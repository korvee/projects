import pygame
from settings import Settings
from ship import Ship
from pygame.sprite import Group
from alien import Alien
from game_stats import GameStats
from button import Button
from scoreboard import Scoreboard
from bullet import Bullet

import sys
from time import sleep
import random


class Alien_Invasion():
    def __init__(self):
        #将游戏依赖的各个组建对象，初始化为游戏主对象的成员，避免这些组建作为参数在各个方法中传递
        #但需要注意，这种方式会导致方法无法复用（因为存在隐式依赖），只适合核心依赖属性，普通依赖对象还是应该作为参数显示传递
        self.ai_settings = Settings()

        pygame.init()
        self.screen = pygame.display.set_mode((self.ai_settings.screen_width,self.ai_settings.screen_height))
        pygame.display.set_caption("Alien invasion")

        self.ship = Ship(self.ai_settings,self.screen)
        self.bullets = Group()
        self.aliens = Group()
        self.game_stats = GameStats(self.ai_settings)
        self.score_board = Scoreboard(self.ai_settings,self.screen,self.game_stats)
        self.play_button = Button(self.ai_settings,self.screen,"Play") 

        

    #->None叫类型检查：python是动态类型的，不对类型进行限制，但通过声明返回类型，可让ide对类型进行检查
    #““““““括起来的叫文档字符串：方法调用时，就会显示出来，提高代码的可阅读性和可维护性
    def check_events(self) -> None:
        """检查键盘和鼠标事件，并基于事件更新组件的状态"""
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                sys.exit()
            elif event.type == pygame.KEYDOWN:
                self.check_keydown_events(event)
            elif event.type == pygame.KEYUP:
                self.check_keyup_events(event)
            elif event.type == pygame.MOUSEBUTTONDOWN:
                mouse_x,mouse_y = pygame.mouse.get_pos()
                self.check_play_button(mouse_x,mouse_y)

    def check_play_button(self,mouse_x,mouse_y):
        button_clicked = self.play_button.rect.collidepoint(mouse_x,mouse_y)
        if button_clicked and not self.game_stats.game_active:
            self.ai_settings.initialize_dymatic_settings()
            pygame.mouse.set_visible(False)
            self.game_stats.reset_stats()
            self.game_stats.game_active = True

            self.score_board.pre_score()
            self.score_board.pre_high_score()
            self.score_board.pre_level()
            self.score_board.pre_ships()

            self.aliens.empty()
            self.bullets.empty()

            self.create_fleet()
            self.ship.center_ship()

    def check_keydown_events(self,event):
        if event.key == pygame.K_RIGHT:
            self.ship.moving_right= True
        elif event.key == pygame.K_LEFT:
            self.ship.moving_left = True
        elif event.key == pygame.K_SPACE:
            self.fire_bullet()
        elif event.key == pygame.K_q:
            sys.exit()

    def check_keyup_events(self,event):
        if event.key == pygame.K_RIGHT:
            self.ship.moving_right = False
        elif event.key == pygame.K_LEFT:
            self.ship.moving_left = False    
            
    def fire_bullet(self):
        #未消失的子弹数小于设定值时才生成
        if len(self.bullets) < self.ai_settings.bullet_allowed:
            new_bullet = Bullet(self.ai_settings,self.screen,self.ship)
            self.bullets.add(new_bullet)

    def update_screen(self):
        self.screen.fill(self.ai_settings.bg_color)
        for bullet in self.bullets.sprites():
            bullet.draw_bullet()
        self.ship.blitme()
        self.aliens.draw(self.screen)

        self.score_board.show_score()

        if not self.game_stats.game_active:
            self.play_button.draw_button()

        pygame.display.flip()

    def update_bullets(self):
        self.bullets.update()
        for bullet in self.bullets.copy():
            if bullet.rect.bottom <= 0:
                self.bullets.remove(bullet)
        self.check_bullet_alien_collisions()
        

    def check_bullet_alien_collisions(self):
        collisions = pygame.sprite.groupcollide(self.bullets,self.aliens,True,True)

        if collisions:
            for aliens in collisions.values():
                self.game_stats.score += len(aliens) * self.ai_settings.alien_points
            self.score_board.pre_score()
            self.check_high_score()

        if len(self.aliens) == 0:
            self.bullets.empty()
            self.ai_settings.increase_speed()

            self.game_stats.level += 1
            self.score_board.pre_level()

            self.create_fleet()

    def create_fleet(self):
        """创建外星人舰队，先计算屏幕可容纳几行几列外星人，然后调用创建外星人方法"""
        # alien = Alien(self.ai_settings,self.screen)
        # available_space_x = self.ai_settings.screen_width - 4 * alien.rect.width
        # number_alien_x = int(available_space_x / (4 * alien.rect.width))

        # available_space_y = (self.ai_settings.screen_height - (3 * alien.rect.height) - self.ship.rect.height)
        # number_rows = int(available_space_y / (2 * alien.rect.height))
        # for row_num in range(number_rows):
        #     for alien_number in range(number_alien_x):
        #         self.create_alien(alien_number,row_num)
        #对创建外星人舰队的方法进行修改，在顶部三行的空间里的一半位置上放置外星人
        alien = Alien(self.ai_settings,self.screen)
        column_nums = int (self.ai_settings.screen_width / (alien.rect.width + 40))
        #先固定放三行
        row_nums = 3
        total_positions = column_nums * row_nums

        positions = [1] * int(total_positions/4) + [0] * (total_positions - int(total_positions / 4))
        random.shuffle(positions)
        for row_num in range(row_nums):
            for column_num in range(column_nums):
                self.create_alien(row_num,column_num,positions[row_num * column_num + column_num])

    def create_alien(self,row_num,column_num,position):
        if not position:
            return
        alien = Alien(self.ai_settings,self.screen)
        alien.x = 20 + (40 + alien.rect.width) * column_num
        alien.rect.x = alien.x
        alien.y = 200 + (40 + alien.rect.height) * row_num
        alien.rect.y = alien.y
        self.aliens.add(alien)

    # def create_alien(self,alien_number,row_number):
    #     alien = Alien(self.ai_settings,self.screen)
    #     alien_width = alien.rect.width
    #     alien.x = alien_width + 4 * alien_width * alien_number
    #     alien.rect.x = alien.x
    #     alien.rect.y = alien.rect.height + 2 * alien.rect.height * row_number
    #     self.aliens.add(alien)

    def check_alliens_bottom(self):
        screen_rect = self.screen.get_rect()
        for alien in self.aliens.sprites():
            if alien.rect.bottom >= screen_rect.bottom:
                self.ship_hit()
                break

    def update_aliens(self):
        self.aliens.update()
        if pygame.sprite.spritecollideany(self.ship,self.aliens):
            self.ship_hit()
        self.check_alliens_bottom()

    def ship_hit(self):
        if self.game_stats.ships_left > 0:
            self.game_stats.ships_left -= 1
            sleep(0.5)
        else:
            self.game_stats.game_active = False
            pygame.mouse.set_visible(True)
            
        self.score_board.pre_ships()

        self.aliens.empty()
        self.bullets.empty()

        self.create_fleet()
        self.ship.center_ship()

    def check_high_score(self):
        if self.game_stats.score > self.game_stats.high_score:
            self.game_stats.high_score = self.game_stats.score
            self.score_board.pre_high_score()

    def run(self):
        
        #创建外星人舰队
        self.create_fleet()

        while True:
            #对鼠标、键盘等事件做出响应，并更新相关组件的状态
            self.check_events()
            
            if self.game_stats.game_active:
                #输入事件以外的事件，主要就是碰撞检测，包括左右移动的飞船又没有接触到屏幕两侧、子弹又没有撞到外星人或者飞出屏幕等
                self.ship.update()
                self.update_bullets()
                self.update_aliens()

            #这里飞船、外星人、子弹、计分板、开始按钮组建的状态已经在事件响应中更新好了，最后统一根据更新后的状态生成画面
            self.update_screen()