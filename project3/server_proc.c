#include <sys/stat.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <signal.h>
#include <semaphore.h>
#include <sys/mman.h>

#define STATS_FILE_PROC "stats_proc.txt"

sem_t *semaphore;

// Function to log the request statistics
void log_request_proc(char *filename, int size, double elapsed_time) { //All we need is to log filename, size, and the elapsed time
    FILE *stats_file = fopen(STATS_FILE_PROC, "a");
    if (stats_file != NULL) {
        fprintf(stats_file, "%s\t%d\t%.4f\n", filename, size, elapsed_time);
        fclose(stats_file);
    } else {
        perror("Error opening stats_proc.txt file");
    }
}


int main()
{
    // Create semaphore
    semaphore = mmap(NULL, sizeof(sem_t), PROT_READ | PROT_WRITE, MAP_SHARED | MAP_ANONYMOUS, -1, 0);
    if (semaphore == MAP_FAILED) {
        perror("Error creating semaphore");
        exit(EXIT_FAILURE);
    }

    if (sem_init(semaphore, 1, 1) == -1) {
        perror("Error initializing semaphore");
        exit(EXIT_FAILURE);
    }

	//Sockets represent potential connections
	//We make an internet socket
	int sfd = socket(PF_INET, SOCK_STREAM, 0);
	if(-1 == sfd)
	{
		perror("Cannot create socket\n");
		exit(EXIT_FAILURE);
	}

	//We will configure it to use this machine's IP, or
	//for us, localhost (127.0.0.1)
	struct sockaddr_in addr;
	memset(&addr, 0, sizeof(addr));

	addr.sin_family = AF_INET;
	//Web servers always listen on port 80
	addr.sin_port = htons(80);
	addr.sin_addr.s_addr = htonl(INADDR_ANY);

	//So we bind our socket to port 80
	if(-1 == bind(sfd, (struct sockaddr *)&addr, sizeof(addr)))
	{
		perror("Bind failed");
		exit(EXIT_FAILURE);
	}

	//And set it up as a listening socket with a backlog of 10 pending connections
	if(-1 == listen(sfd, 10))
	{
		perror("Listen failed");
		exit(EXIT_FAILURE);
	}

	//A server's gotta serve...
	for(;;)
	{
		//accept() blocks until a client connects. When it returns,
		//we have a client that we can do client stuff with.
		int connfd = accept(sfd, NULL, NULL);
		if(connfd < 0)
		{
			perror("Accept failed");
			exit(EXIT_FAILURE);
		}

		//At this point a client has connected. The remainder of the
		//loop is handling the client's GET request and producing
		//our response.

        // Fork a new process to handle the client request
        pid_t pid = fork();
        if (pid < 0) {
            perror("Fork failed");
            exit(EXIT_FAILURE);
        } else if (pid == 0) {
            // Child process
            close(sfd);  // Close the server socket in the child process
            struct timespec start_time, end_time;
			clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &start_time); //START ELAPSED TIME CLOCK


		char buffer[1024];
		char filename[1024];
		FILE *f;

		memset(buffer, sizeof(buffer), 0);
		memset(filename, sizeof(buffer), 0);

		//In HTTP, the client speaks first. So we recv their message
		//into our buffer.
		int amt = recv(connfd, buffer, sizeof(buffer), 0);
		fprintf(stderr, "%s", buffer);

		//We only can handle HTTP GET requests for files served
		//from the current working directory, which becomes the website root
		if(sscanf(buffer, "GET /%s", filename)<1) {
			fprintf(stderr, "Bad HTTP request\n");
			close(connfd);
			continue;
		}

		//If the HTTP request is bigger than our buffer can hold, we need to call
		//recv() until we have no more data to read, otherwise it will be
		//there waiting for us on the next call to recv(). So we'll just
		//read it and discard it. GET should be the first 3 bytes, and we'll
		//assume paths that are smaller than about 1000 characters.
		if(amt == sizeof(buffer))
		{		
			//if recv returns as much as we asked for, there may be more data
			while(recv(connfd, buffer, sizeof(buffer), 0) == sizeof(buffer))
				/* discard */;
		}

		//if we don't open for binary mode, line ending conversion may occur.
		//this will make a liar our of our file size.
		f = fopen(filename, "rb");
		if(f == NULL)
		{
			//Assume that failure to open the file means it doesn't exist
			strcpy(buffer, "HTTP/1.1 404 Not Found\n\n");
			send(connfd, buffer, strlen(buffer), 0);
		}
		else
		{
			int size;
			char response[1024];

			strcpy(response, "HTTP/1.1 200 OK\n");
			//nanosleep(&((struct timespec){1,0}),NULL); //These nanosleeps were used to check for parrallelism
			send(connfd, response, strlen(response), 0);

			time_t now;
			time(&now);
			//How convenient that the HTTP Date header field is exactly
			//in the format of the asctime() library function.
			//
			//asctime adds a newline for some dumb reason.
			sprintf(response, "Date: %s", asctime(gmtime(&now)));
			//nanosleep(&((struct timespec){1,0}),NULL);
			send(connfd, response, strlen(response), 0);

			//Get the file size via the stat system call
			struct stat file_stats;
			fstat(fileno(f), &file_stats);
			sprintf(response, "Content-Length: %d\n", file_stats.st_size);
			//nanosleep(&((struct timespec){1,0}),NULL);
			send(connfd, response, strlen(response), 0);

			//Tell the client we won't reuse this connection for other files
			strcpy(response, "Connection: close\n");
			//nanosleep(&((struct timespec){1,0}),NULL);
			send(connfd, response, strlen(response), 0);

			//Send our MIME type and a blank line
			strcpy(response, "Content-Type: text/html\n\n");
			//nanosleep(&((struct timespec){1,0}),NULL);
			send(connfd, response, strlen(response), 0);

			fprintf(stderr, "File: %s\n", filename);

			int bytes_read = 0;
			do
			{
				//read response amount of data at a time.
				//Note that sizeof() in C can only tell us the number of
				//elements in an array that is declared in scope. If you
				//move the declaration elsewhere, it will degrade into
				//the sizeof a pointer instead.
				bytes_read = fread(response, 1, sizeof(response), f);

				//if we read anything, send it
				if(bytes_read > 0)
				{
					int sent = send(connfd, response, bytes_read, 0);
					//It's possible that send wasn't able to send all of
					//our response in one call. It will return how much it
					//actually sent. Keep calling send until all of it is
					//sent.
					while(sent < bytes_read)
					{
						sent += send(connfd, response+sent, bytes_read-sent, 0);
					}
				}
			} while(bytes_read > 0 && bytes_read == sizeof(response));

			fclose(f);
			  // Calculate and log the elapsed time
            clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &end_time); //END ELAPSED TIME CLOCK
			double elapsed_time = (end_time.tv_sec - start_time.tv_sec) + (end_time.tv_nsec - start_time.tv_nsec) / 1e9; //CALCULATE THE TIME

             sem_wait(semaphore); //critial region
            log_request_proc(filename, file_stats.st_size, elapsed_time); 

            sem_post(semaphore); //critical region end

            exit(EXIT_SUCCESS); 

		}
		shutdown(connfd, SHUT_RDWR);
		close(connfd);

        exit(EXIT_SUCCESS); 

	} //end of child

        close(connfd);
    }     
    
	close(sfd);
	sem_destroy(semaphore);
	return 0;
}

