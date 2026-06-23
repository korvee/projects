import pygame
from pygame.sprite import Sprite

class Alien(Sprite):
    
    def __init__(self, ai_settings,screen):
        super(Alien,self).__init__()
        self.screen = screen
        self.ai_settings = ai_settings

        self.image = pygame.image.load('images/alien.bmp')
        self.image = pygame.transform.scale(self.image,(50,50))
        self.rect = self.image.get_rect()

        self.rect.x = self.rect.width
        self.rect.y = self.rect.height

        self.x = float(self.rect.x)
        self.y = float(self.rect.y)

    def update(self):
        #x坐标不动，一直往下落就行了
        self.y += self.ai_settings.alien_speed_factor
        self.rect.y = self.y

    def blitme(self):
        self.screen.blit(self.image,self.rect)
