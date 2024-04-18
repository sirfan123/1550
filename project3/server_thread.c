#include <sys/stat.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <signal.h>
#include <pthread.h>

#define STATS_FILE_THREAD "stats_thread.txt"

pthread_mutex_t mutex;

// Function to log the request statistics
void log_request_thread(char *filename, int size, double elapsed_time) { //We need to log file name size and elapsed time
    FILE *stats_file = fopen(STATS_FILE_THREAD, "a");
    if (stats_file != NULL) {
        fprintf(stats_file, "%s\t%d\t%.4f\n", filename, size, elapsed_time);
        fclose(stats_file);
    } else {
        perror("Error opening stats_thread.txt file");
    }
}

// Function to handle a client request in a thread because threads make function
void *handle_request_thread(void *arg) {
     struct timespec start_time, end_time;
    clock_gettime(CLOCK_THREAD_CPUTIME_ID, &start_time); //Start elapsed time
    int connfd = *((int *)arg);
    char buffer[1024];
    char filename[1024];
    FILE *f;

    memset(buffer, 0, sizeof(buffer));
    memset(filename, 0, sizeof(filename));

    //In HTTP, the client speaks first. So we recv their message
    //into our buffer.
    int amt = recv(connfd, buffer, sizeof(buffer), 0);
    fprintf(stderr, "%s", buffer);

    //We only can handle HTTP GET requests for files served
    //from the current working directory, which becomes the website root
    if (sscanf(buffer, "GET /%s", filename) < 1) {
        fprintf(stderr, "Bad HTTP request\n");
        close(connfd);
        pthread_exit(NULL);
    }

    //If the HTTP request is bigger than our buffer can hold, we need to call
    //recv() until we have no more data to read, otherwise it will be
    //there waiting for us on the next call to recv(). So we'll just
    //read it and discard it. GET should be the first 3 bytes, and we'll
    //assume paths that are smaller than about 1000 characters.
    if (amt == sizeof(buffer)) {
        //if recv returns as much as we asked for, there may be more data
        while (recv(connfd, buffer, sizeof(buffer), 0) == sizeof(buffer))
            /* discard */;
    }

    //if we don't open for binary mode, line ending conversion may occur.
    //this will make a liar our of our file size.
    f = fopen(filename, "rb");
    if (f == NULL) {
        //Assume that failure to open the file means it doesn't exist
        char response[] = "HTTP/1.1 404 Not Found\n\n";
        send(connfd, response, strlen(response), 0);
    } else {
        int size;
        char response[1024];

        strcpy(response, "HTTP/1.1 200 OK\n");
        //nanosleep(&((struct timespec){1,0}),NULL); THESE NANOSLEEPS WERE USED TO SEE IF PARRALLEISM WAS ACHIEVED SUCCESSFULY
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
        do {
            //read response amount of data at a time.
            //Note that sizeof() in C can only tell us the number of
            //elements in an array that is declared in scope. If you
            //move the declaration elsewhere, it will degrade into
            //the sizeof a pointer instead.
            bytes_read = fread(response, 1, sizeof(response), f);

            //if we read anything, send it
            if (bytes_read > 0) {
                int sent = send(connfd, response, bytes_read, 0);
                //It's possible that send wasn't able to send all of
                //our response in one call. It will return how much it
                //actually sent. Keep calling send until all of it is
                //sent.
                while (sent < bytes_read) {
                    sent += send(connfd, response + sent, bytes_read - sent, 0);
                }
            }
        } while (bytes_read > 0 && bytes_read == sizeof(response));

        fclose(f);

      // Calculate and log the elapsed time
    clock_gettime(CLOCK_THREAD_CPUTIME_ID, &end_time); //END ELAPSING TIME
    double elapsed_time = (end_time.tv_sec - start_time.tv_sec) + (end_time.tv_nsec - start_time.tv_nsec) / 1e9; //Calculate elapsed time


    pthread_mutex_lock(&mutex); //Lock critial region we dont want to overwrite shared data
    log_request_thread(filename, file_stats.st_size, elapsed_time); 
    pthread_mutex_unlock(&mutex); // Unlock the critical section
    
    }
     // Close the connection
    shutdown(connfd, SHUT_RDWR);
    close(connfd);

    pthread_exit(NULL);
}

int main() {
    // Initialize mutex
    if (pthread_mutex_init(&mutex, NULL) != 0) {
        perror("Mutex initialization failed");
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
    for (;;) {
        int connfd = accept(sfd, NULL, NULL);
        if (connfd < 0) {
            perror("Accept failed");
            exit(EXIT_FAILURE);
        }

        pthread_t tid;
        if (pthread_create(&tid, NULL, handle_request_thread, &connfd) != 0) { //Create our thread
            perror("Thread creation failed");
            exit(EXIT_FAILURE);
        }

        pthread_detach(tid); // Detach the thread to avoid memory leaks
    }

    // Close the server socket and destroy the mutex
    close(sfd);
    pthread_mutex_destroy(&mutex);

    return 0;
}
