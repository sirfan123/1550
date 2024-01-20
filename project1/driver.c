#include "library.h"

int main(int argc, char** argv)
{
	init_graphics();

	char key;
	int x = (640-20)/2;
	int y = (480-20)/2;

	do
	{
		draw_text(210,210,"Press 'q' to exit.", 65535);
		circle(x, y, 20, 15);

		key = getkey();
		if(key == 'w') y-=10;
		else if(key == 's') y+=10;
		else if(key == 'a') x-=10;
		else if(key == 'd') x+=10;

		//draw a blue rectangle
		draw_rect(x, y, 20, 20, 15);
		sleep_ms(20);
	} while(key != 'q');

	exit_graphics();

	return 0;

}
