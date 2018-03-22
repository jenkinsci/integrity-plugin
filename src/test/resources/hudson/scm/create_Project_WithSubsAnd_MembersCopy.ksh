maxM=${1:-2}
max1=${2:-5}
max2=${3:-5}
max3=${4:-5}
max4=${5:-5}

si connect --hostname=localhost --port=7001 --user=Administrator --password=password 
# Give bypasscp permission to everyone
aa addaclentry --acl=mks:si g=everyone:BypassChangePackageMandatory

name="JenkinsBulkProject1"
si createproject /$name/project.pj
si createsandbox -P /$name/project.pj sbx$name
cd sbx$name
let s1=0; 
while [ $s1 -ne $max1 ] ; do
si createsubproject --cpid=:bypass sub$s1/project.pj
cd sub$s1
   let i=0;
   while [ $i -ne $maxM ] ; do
   echo "Example text." > mbr-$s1-$i.txt
   let i=$i+1
   done
   si add --cpid=:bypass m*.txt

	let s2=0; 
	while [ $s2 -ne $max2 ] ; do
	si createsubproject --cpid=:bypass sub$s1-$s2/project.pj
		cd sub$s1-$s2
		let i=0;
		while [ $i -ne $maxM ] ; do
		echo "Example text." > mbr-$s1-$s2-$i.txt
		let i=$i+1
		done
		si add --cpid=:bypass m*.txt

			let s3=0; 
			while [ $s3 -ne $max3 ] ; do
			si createsubproject --cpid=:bypass sub$s1-$s2-$s3/project.pj
			cd sub$s1-$s2-$s3
				let i=0;
				while [ $i -ne $maxM ] ; do
				echo "Example text." > mbr-$s1-$s2-$s3-$i.txt
				let i=$i+1
				done
				si add --cpid=:bypass m*.txt
			
			
						let s4=0; 
						while [ $s4 -ne $max4 ] ; do
						si createsubproject --cpid=:bypass sub$s1-$s2-$s3-$s4/project.pj
						cd sub$s1-$s2-$s3-$s4			
							let i=0;
							while [ $i -ne $maxM ] ; do
							echo "Example text." > mbr-$s1-$s2-$s3-$s4-$i.txt
							let i=$i+1
							done
							si add --cpid=:bypass m*.txt
			
						cd ..
						let s4=$s4+1
						done

			cd ..
			let s3=$s3+1
			done
      cd ..
      let s2=$s2+1
      done
cd ..
let s1=$s1+1   
done

si checkpoint --checkpointUnchangedSubprojects --label=Create_DP --description=Create_DP --project=/$name/project.pj
si createdevpath --devpath=DP_0.3813840334796077 --projectRevision=Create_DP -P /$name/project.pj