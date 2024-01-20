#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/fb.h>
#include <termios.h>
#include <unistd.h>
#include <time.h>
#include "iso_font.h"

/*Global variables */
typedef unsigned short color_t;
int fd;
struct fb_var_screeninfo varInfo;
struct fb_fix_screeninfo fixInfo;
struct termios oldTermios;
struct termios newTermios;
size_t mapSize;
void *addr;
int numPixels;

/*Initializes the graphics device*/
void init_graphics(){
	
	//Opens the file representing first frame buffer 
	fd = open("/dev/fb0", O_RDWR);
	
	//Find screen resolution and bit depth 
    ioctl(fd, FBIOGET_VSCREENINFO, &varInfo)
    ioctl(fd, FBIOGET_FSCREENINFO, &fixInfo)
	mapSize = fixInfo.line_length * varInfo.yres_virtual;
	
	//Writes to void * mmap
	addy = mmap(NULL, mapSize, PROT_READ|PROT_WRITE, MAP_SHARED, fd, 0);
	
	//Disable canonical mode and keypress echo 
	ioctl(fd, TCGETS, &oldTermios);
	newTermios = oldTermios;
	newTermios.c_lflag &= ~ICANON;
	newTermios.c_lflag &= ~ECHO;
	ioctl(fd, TCSETS, &newTermios);
}

//Reenables canon and echo, then closes the map and file 
void exit_graphics(){

	ioctl(fd, TCSETS, &oldTermios);

	munmap(addy, mapSize);
	close(fd);
}

//Prompts the user to enter a character and returns that character 
char getkey(){
	fd_set rfds;
	int retval;
	char buff = 0;
	ssize_t retIn;
	FD_ZERO(&rfds);
	FD_SET(0, &rfds);
	
	/*Calls select then read, so that the call isn't blocked*/
	retval = select(1, &rfds, NULL, NULL, NULL);
	if(retval != -1){
		retIn = read(0, &buff, 1);
	}
	return buff;
}

//Delays action
void sleep_ms(long ms){
	struct timespec time;
	time.tv_sec = 0;
	time.tv_nsec = ms*1000000;
	
	if (nanosleep(&time, NULL)==-1){
	}
}

//Draws a pixel at location x and y with a passed in color
void draw_pixel(int x, int y, color_t color){
	unsigned short *pixel = (unsigned short*) addr;
	int location;
	location = x +(y*fixInfo.line_length);
	*(pixel+location) = color;
}

//Draws a rectangle
void draw_rect(int x1, int y1, int width, int height, color_t c) {
    draw_pixel(x1,y1,color);
    draw_pixel(x1+width,y1,color);
    draw_pixel(x1+width,y1+height,color);
    draw_pixel(x1,y1+height,color);
}

void draw_text(int x, int y, const char *text, color_t  c) {
    int ascii, i, j;
	unsigned char iso;
	unsigned int mask;
	int count = 0;
	
	char textIndex = text[count];
	
	while(textIndex!='\0'){
		ascii = (int)textIndex;
		
		for (i = 0; i < 16; i++) {
			iso = iso_font[ascii*16+i];
			j = 0;
			for (mask = 0x80; mask != 0; mask >>= 1) {

				if (iso & mask) {
					draw_pixel(x-j,y+i, color);
				}
				j++;
			}
      
		}
		x+=8;
		textIndex = text[++count];
	}
}
