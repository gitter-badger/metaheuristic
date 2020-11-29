docker run -it --rm --name %project% -v "%pr-path%\%project-web%":/usr/src/%project-web% -w /usr/src/%project-web% -v "%pr-path%\node_modules":/usr/src/%project-web%/node_modules node:lts-alpine3.11 npm set progress=false && npm install && node --max_old_space_size=2048 %pr-path%\node_modules\@angular\cli\bin\ng build --aot --prod=true